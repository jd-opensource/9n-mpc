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

use crate::api::{PsiExecuteRequest, PsiExecuteResult, RequestHeader, ResultHeader};
use crate::front::err::AppError;
use prost_types::Any;
use std::collections::HashMap;

use axum::{
    async_trait,
    body::Bytes,
    extract::{FromRequest, Request},
    response::{IntoResponse, Response},
};
use prost::Message;

#[async_trait]
impl<S> FromRequest<S> for PsiExecuteRequest
where
    S: Send + Sync,
{
    type Rejection = AppError;

    async fn from_request(req: Request, state: &S) -> Result<Self, Self::Rejection> {
        let body = Bytes::from_request(req, state).await?;

        let req: PsiExecuteRequest = PsiExecuteRequest::decode(body)?;

        Ok(req)
    }
}

impl IntoResponse for PsiExecuteResult {
    fn into_response(self) -> Response {
        self.encode_to_vec().into_response()
    }
}

pub fn back_header(header: &Option<RequestHeader>) -> Option<ResultHeader> {
    if header.is_none() {
        Some(ResultHeader {
            request_id: "".to_string(),
            metadata: HashMap::<String, Any>::new(),
            code: 0,
            msg: "".to_string(),
        })
    } else {
        let value = header.as_ref().unwrap();
        Some(ResultHeader {
            request_id: value.request_id.clone(),
            metadata: value.metadata.clone(),
            code: 0,
            msg: "".to_string(),
        })
    }
}
