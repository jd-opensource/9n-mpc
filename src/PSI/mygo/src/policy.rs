pub mod batcher;
pub mod stream;

use crate::api::{PsiExecuteRequest, PsiExecuteResult};
use crate::execute::client::Client;
use crate::front::err::AppError;
use crate::policy::batcher::Batcher;
use axum::async_trait;
use std::mem::transmute;
use std::sync::Arc;

#[async_trait]
pub trait PolicyImpl: Send + Sync {
    async fn execute(&self, req: &PsiExecuteRequest) -> Result<PsiExecuteResult, AppError>;
    async fn shutdown(&mut self);
}

struct defaultPolicy {
    client: Arc<Client>,
}

#[async_trait]
impl PolicyImpl for defaultPolicy {
    async fn execute(&self, req: &PsiExecuteRequest) -> Result<PsiExecuteResult, AppError> {
        self.client.psi_execute(req).await
    }

    async fn shutdown(&mut self) {}
}

pub struct Policy {
    ipl: Box<dyn PolicyImpl>,
}

impl Policy {
    pub async fn new(conf: &PolicyConf, client: Arc<Client>) -> Result<Policy, AppError> {
        let ipl = match conf {
            PolicyConf::BatcherConf(conf) => {
                Batcher::new(
                    client.clone(),
                    conf.workers,
                    conf.duration_ms,
                    conf.cache,
                    conf.batch_size,
                )
                .await?
            }
            _ => Box::new(defaultPolicy {
                client: client.clone(),
            }),
        };

        Ok(Policy { ipl })
    }

    pub async fn execute(&self, req: &PsiExecuteRequest) -> Result<PsiExecuteResult, AppError> {
        self.ipl.execute(req).await
    }

    pub async fn shutdown(&self) {
        unsafe {
            let this: u64 = transmute::<&Policy, u64>(self);
            let this = transmute::<u64, &mut Policy>(this);
            this.ipl.shutdown().await;
        }
    }
}

pub struct BatcherPolicyConf {
    // pub threhold: usize,
    pub workers: usize,
    pub duration_ms: usize,
    pub cache: usize,
    pub batch_size: usize,
}

pub enum PolicyConf {
    BatcherConf(BatcherPolicyConf),
    DefaultConf,
}
