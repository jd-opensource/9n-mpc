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

pub mod client;
pub mod serve;

use crate::api::execute_service_server::ExecuteServiceServer;
use crate::api::{PsiExecuteRequest, PsiExecuteResult};
use crate::encrypt::Curve;
use crate::execute::client::Client;
use crate::execute::serve::ExecuteServiceImpl;
use crate::front::err::AppError;
use crate::policy::{Policy, PolicyConf};
use hyper::service::make_service_fn;
use local_ip_address::local_ip;
use redis::Commands;
use std::convert::Infallible;
use std::fmt::Debug;
use std::net::SocketAddr;
use std::str::FromStr;
use std::sync::Arc;
use tokio::spawn;
use tokio::sync::Notify;
use tokio::time::{sleep, Duration};

pub struct ExecuteEngine {
    client: Arc<Client>,
    exit_sig: Arc<Notify>,
    policy: Policy,
}

impl Debug for ExecuteEngine {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ExecuteEngine").finish()
    }
}

impl ExecuteEngine {
    pub async fn new(
        key: String,
        curve: String,
        host: String,
        remote: String,
        target: String,
        id: String,
        redis_address: String,
        redis_password: String,
        policy_conf: PolicyConf,
    ) -> Result<Self, AppError> {
        //for debug
        if !redis_address.is_empty() {
            let redis_url = {
                if redis_password.is_empty() {
                    format!("redis://{redis_address}/")
                } else {
                    format!("redis://:{redis_password}@{redis_address}")
                }
            };

            redis::Client::open(redis_url)?.get_connection()?.set(
                format!("network:{}", id),
                format!(
                    "{}:{}",
                    local_ip()?.to_string(),
                    SocketAddr::from_str(&host)?.port()
                ),
            )?;
        }

        let exit_sig = Arc::new(Notify::new());
        let server_curve = Curve::new(key.as_bytes(), &curve);
        let exit_sig2 = exit_sig.clone();
        let _ = spawn(async move {
            let service = ExecuteServiceServer::new(ExecuteServiceImpl::new(server_curve));
            let grpc_service = tonic::transport::Server::builder()
                .add_service(
                    service
                        .max_decoding_message_size(usize::max_value())
                        .max_encoding_message_size(usize::max_value()),
                )
                .into_service();
            let builder = hyper::Server::bind(&SocketAddr::from_str(&host).unwrap());

            let make_grpc_service = make_service_fn(move |_conn| {
                let grpc_service = grpc_service.clone();
                async { Ok::<_, Infallible>(grpc_service) }
            });

            builder
                .serve(make_grpc_service)
                .with_graceful_shutdown(async {
                    exit_sig2.notified().await;
                })
                .await
                .expect("Error with HTTP server!");
            exit_sig2.notify_waiters();
        });

        //for debug
        let _ = sleep(Duration::from_secs(1)).await;
        let client =
            Arc::new(Client::new(Curve::new(key.as_bytes(), &curve), remote, target, id).await?);

        let policy = Policy::new(&policy_conf, client.clone()).await?;

        Ok(ExecuteEngine {
            client: client,
            exit_sig: exit_sig,
            policy: policy,
        })
    }

    pub async fn shutdown(&self) {
        self.exit_sig.notify_waiters();

        tokio::select! {
            _  = async {self.exit_sig.notified().await;} => {
            },
            _ = async {sleep(Duration::from_secs(2)).await;} => {
            }
        }
    }

    pub async fn psi_execute(&self, req: &PsiExecuteRequest) -> Result<PsiExecuteResult, AppError> {
        self.policy.execute(req).await
    }
}
