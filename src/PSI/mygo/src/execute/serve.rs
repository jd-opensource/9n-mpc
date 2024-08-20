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

use crate::api::execute_service_server::ExecuteService;
use crate::api::{PsiExecuteRequest, PsiExecuteResult};
use crate::encrypt::Curve;
use crate::front::cover::back_header;
use crate::front::err::AppError;
use h2;
use std::mem::transmute;
use std::{error::Error, io::ErrorKind, pin::Pin};
use tokio::sync::mpsc;
use tokio_stream::{wrappers::ReceiverStream, Stream, StreamExt};
use tonic::{async_trait, Status};

pub(crate) struct ExecuteServiceImpl {
    curve: Curve,
}

fn match_for_io_error(err_status: &Status) -> Option<&std::io::Error> {
    let mut err: &(dyn Error + 'static) = err_status;

    loop {
        if let Some(io_err) = err.downcast_ref::<std::io::Error>() {
            return Some(io_err);
        }

        if let Some(h2_err) = err.downcast_ref::<h2::Error>() {
            if let Some(io_err) = h2_err.get_io() {
                return Some(io_err);
            }
        }

        err = match err.source() {
            Some(err) => err,
            None => return None,
        };
    }
}

#[async_trait]
impl ExecuteService for ExecuteServiceImpl {
    async fn psi_execute(
        &self,
        request: tonic::Request<PsiExecuteRequest>,
    ) -> Result<tonic::Response<PsiExecuteResult>, Status> {
        self.psi_execute_impl(&request)
            .or_else(|err| Ok(err.into_tonic_response(&request.get_ref().header)))
    }

    type PsiStreamExecuteStream =
        Pin<Box<dyn Stream<Item = Result<PsiExecuteResult, Status>> + Send>>;

    async fn psi_stream_execute(
        &self,
        request: tonic::Request<tonic::Streaming<PsiExecuteRequest>>,
    ) -> std::result::Result<tonic::Response<Self::PsiStreamExecuteStream>, Status> {
        let (tx, rx) = mpsc::channel::<Result<PsiExecuteResult, Status>>(128);
        let mut incoming = request.into_inner();
        unsafe {
            let this = transmute::<&ExecuteServiceImpl, u64>(self);

            tokio::spawn(async move {
                let this = transmute::<u64, &ExecuteServiceImpl>(this);

                while let Some(result) = incoming.next().await {
                    match result {
                        Ok(msg) => {
                            let _ = tx
                                .send(this.psi_stream_execute_impl(&msg).or_else(|err| {
                                    Ok::<PsiExecuteResult, Status>(PsiExecuteResult::from(err))
                                }))
                                .await;
                        }
                        Err(err) => {
                            if let Some(io_err) = match_for_io_error(&err) {
                                if io_err.kind() == ErrorKind::BrokenPipe {
                                    break;
                                }
                            }

                            match tx.send(Err(err)).await {
                                Ok(_) => (),
                                Err(_err) => break,
                            }
                        }
                    }
                }
            });
        }

        let outbound = ReceiverStream::new(rx);
        Ok(tonic::Response::new(
            Box::pin(outbound) as Self::PsiStreamExecuteStream
        ))
    }
}

impl ExecuteServiceImpl {
    fn psi_execute_impl(
        &self,
        request: &tonic::Request<PsiExecuteRequest>,
    ) -> Result<tonic::Response<PsiExecuteResult>, AppError> {
        let keys = self.curve.encrypt_peer(&request.get_ref().keys)?;
        Ok(tonic::Response::new(PsiExecuteResult {
            header: back_header(&request.get_ref().header),
            keys: keys,
        }))
    }

    fn psi_stream_execute_impl(
        &self,
        request: &PsiExecuteRequest,
    ) -> Result<PsiExecuteResult, AppError> {
        let keys = self.curve.encrypt_peer(&request.keys)?;
        Ok(PsiExecuteResult {
            header: back_header(&request.header),
            keys: keys,
        })
    }

    pub fn new(curve: Curve) -> ExecuteServiceImpl {
        ExecuteServiceImpl { curve: curve }
    }
}
