// Copyright 2020 The 9nFL Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use crate::api::execute_service_client::ExecuteServiceClient;
use crate::api::{PsiExecuteRequest, PsiExecuteResult};
use crate::encrypt::Curve;
use crate::front::err::AppError;
use futures::StreamExt;
use std::iter::Iterator;
use std::mem::transmute;
use std::sync::Arc;
use tokio::sync::RwLock;
use tonic::transport::{Certificate, Channel, ClientTlsConfig, Identity};
use tonic::{metadata::MetadataValue, transport::Endpoint, Status};

pub struct Client {
    curve: Curve,
    client: Arc<RwLock<ExecuteServiceClient<Channel>>>,
    target: String,
    id: String,
    remote: String,
    use_tls: bool,
    cert_path: String,
}

impl Client {
    async fn create_client(
        remote: String,
        use_tls: bool,
        cert_path: String,
        lazy: bool,
    ) -> Result<ExecuteServiceClient<Channel>, AppError> {
        let mut conn = Endpoint::new(remote)?;

        if use_tls {
            let dir = std::path::PathBuf::from(cert_path);
            let cert = std::fs::read_to_string(dir.join("ca.pem")).unwrap();
            let ca = Certificate::from_pem(cert);
            let tls = ClientTlsConfig::new().ca_certificate(ca).domain_name("mygo");

            conn = conn.tls_config(tls)?;
        }

        let conn = {
            if lazy {
                conn.connect_lazy()
            } else {
                conn.connect().await?
            }
        };

        Ok(ExecuteServiceClient::new(conn)
            .max_decoding_message_size(usize::max_value())
            .max_encoding_message_size(usize::max_value()))
    }

    pub async fn new(
        curve: Curve,
        remote: String,
        target: String,
        id: String,
        use_tls: bool,
        cert_path: String,
    ) -> Result<Client, AppError> {
        let client = Arc::new(RwLock::new(
            Client::create_client(remote.clone(), use_tls, cert_path.clone(), true).await?,
        ));

        Ok(Client {
            curve,
            client,
            target,
            id,
            remote,
            use_tls,
            cert_path,
        })
    }

    async fn retry_connect(&self, status: &Status) {
        if status.code() == tonic::Code::Unavailable {
            let no_creating_client = self.client.try_write();
            if no_creating_client.is_err() {
                return;
            } else {
                let conn = Client::create_client(
                    self.remote.clone(),
                    self.use_tls,
                    self.cert_path.clone(),
                    false,
                )
                .await;
                if conn.is_err() {
                    tracing::error!("reconnect to remote failed");
                } else {
                    *no_creating_client.unwrap() = conn.unwrap();
                    tracing::info!("reconnect to remote success");
                }
            }
        }
    }
}

impl Client {
    pub async fn psi_execute(&self, req: &PsiExecuteRequest) -> Result<PsiExecuteResult, AppError> {
        let keys = self.curve.encrypt_self(&req.keys)?;
        let req = PsiExecuteRequest {
            keys: keys,
            ..req.clone()
        };

        let mut request: tonic::Request<PsiExecuteRequest> = tonic::Request::new(req);
        request
            .metadata_mut()
            .insert("id", MetadataValue::try_from(&self.id)?);
        request
            .metadata_mut()
            .insert("target", MetadataValue::try_from(&self.target)?);

        match self.client.read().await.clone().psi_execute(request).await {
            Ok(resp) => Ok(resp.into_inner()),
            Err(e) => {
                self.retry_connect(&e).await;
                Err(AppError::from(e))
            }
        }
    }

    pub fn encrypt_req(&self, req: &PsiExecuteRequest) -> Result<PsiExecuteRequest, AppError> {
        let keys = self.curve.encrypt_self(&req.keys)?;
        let req = PsiExecuteRequest {
            keys: keys,
            ..req.clone()
        };

        Ok(req)
    }

    pub async fn psi_execute_without_encrypt(
        &self,
        req: &PsiExecuteRequest,
    ) -> Result<PsiExecuteResult, AppError> {
        let mut request: tonic::Request<PsiExecuteRequest> = tonic::Request::new(req.to_owned());
        request
            .metadata_mut()
            .insert("id", MetadataValue::try_from(&self.id)?);
        request
            .metadata_mut()
            .insert("target", MetadataValue::try_from(&self.target)?);

        match self.client.read().await.clone().psi_execute(request).await {
            Ok(resp) => Ok(resp.into_inner()),
            Err(e) => {
                self.retry_connect(&e).await;
                Err(AppError::from(e))
            }
        }
    }

    pub async fn psi_stream_execute<
        T: Iterator<Item = PsiExecuteRequest> + Send + Sync + 'static,
        // R: Iterator<Item = Result<PsiExecuteResult, AppError>>,
    >(
        &self,
        req_iter: T,
    ) -> Result<Vec<Result<PsiExecuteResult, AppError>>, AppError> {
        let mut results: Vec<Result<PsiExecuteResult, AppError>> = Vec::new();
        unsafe {
            let this: u64 = transmute::<&Client, u64>(self);

            let requests_stream = tokio_stream::iter(req_iter.map(move |req| {
                let this = transmute::<u64, &Client>(this);
                let keys = this.curve.encrypt_self(&req.keys).unwrap_or(Vec::new());
                PsiExecuteRequest { keys: keys, ..req }
            }));

            let response = self
                .client
                .read()
                .await
                .clone()
                .psi_stream_execute(requests_stream)
                .await?;

            let mut resp_stream = response.into_inner();

            while let Some(received) = resp_stream.next().await {
                results.push(received.map_err(|e| AppError::from(e)));
            }

            Ok(results)
        }
    }
}
