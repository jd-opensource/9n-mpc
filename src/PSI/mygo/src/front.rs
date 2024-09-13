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

pub mod cover;
pub mod err;

use crate::api::PsiExecuteRequest;
use crate::execute::ExecuteEngine;
use crate::front::err::AppError;
use axum::{
    extract::State,
    response::{IntoResponse, Response},
};
use std::sync::Arc;

#[derive(Clone)]
pub struct AppStateDyn {
    pub engine: Arc<ExecuteEngine>,
}

pub async fn do_psi(
    state: State<AppStateDyn>,
    req: PsiExecuteRequest,
) -> Result<Response, AppError> {
    do_psi_impl(state, &req).await.or_else(|err| {
        Ok(err
            .into_tonic_response(&req.header)
            .get_ref()
            .clone()
            .into_response())
    })
}

pub async fn do_psi_impl(
    state: State<AppStateDyn>,
    req: &PsiExecuteRequest,
) -> Result<Response, AppError> {
    let rsp = state.engine.psi_execute(&req).await?;

    {
        let request_info = rsp.header.clone().unwrap();
        if request_info.code == 0 {
            tracing::debug!(
                "request_id[{}] code:[{}] msg:[{}]",
                request_info.request_id,
                request_info.code,
                request_info.msg,
            );
        } else {
            tracing::error!(
                "request_id[{}] code:[{}] msg:[{}]",
                request_info.request_id,
                request_info.code,
                request_info.msg,
            );
        }
        
    }

    Ok(rsp.into_response())
}
