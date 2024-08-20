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

use crate::api::{PsiExecuteResult, RequestHeader, ResultHeader};
use anyhow::Error;
use axum::response::{IntoResponse, Response};
use std::collections::HashMap;

#[derive(Debug)]
pub struct AppError {
    pub err: Error,
}

impl AppError {
    pub fn new(err: String) -> Self {
        AppError {
            err: Error::msg(err),
        }
    }
}

impl Clone for AppError {
    fn clone(&self) -> Self {
        AppError {
            err: Error::msg(self.err.to_string()),
        }
    }
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        PsiExecuteResult {
            header: Some(ResultHeader {
                request_id: "".to_string(),
                metadata: HashMap::new(),
                code: -1,
                msg: format!("err:{}", self.err),
            }),
            keys: Vec::new(),
        }
        .into_response()
    }
}

impl AppError {
    pub fn into_tonic_response(
        self,
        header: &Option<RequestHeader>,
    ) -> tonic::Response<PsiExecuteResult> {
        if header.is_none() {
            tonic::Response::new(PsiExecuteResult {
                header: Some(ResultHeader {
                    request_id: "".to_string(),
                    metadata: HashMap::new(),
                    code: -1,
                    msg: format!("err:{}", self.err),
                }),
                keys: Vec::new(),
            })
        } else {
            let value = header.as_ref().unwrap();
            tonic::Response::new(PsiExecuteResult {
                header: Some(ResultHeader {
                    request_id: value.request_id.clone(),
                    metadata: value.metadata.clone(),
                    code: -1,
                    msg: format!("err:{}", self.err),
                }),
                keys: Vec::new(),
            })
        }
    }
}

impl From<AppError> for PsiExecuteResult {
    fn from(err: AppError) -> Self {
        PsiExecuteResult {
            header: Some(ResultHeader {
                request_id: "".to_string(),
                metadata: HashMap::new(),
                code: -1,
                msg: format!("err:{}", err.err),
            }),
            keys: Vec::new(),
        }
    }
}

impl<E> From<E> for AppError
where
    E: Into<anyhow::Error>,
{
    fn from(err: E) -> Self {
        Self { err: err.into() }
    }
}
