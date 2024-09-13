use crate::api::{PsiExecuteRequest, PsiExecuteResult};
use crate::execute::client::Client;
use crate::front::cover::back_header;
use crate::front::err::AppError;
use crate::policy::PolicyImpl;
use axum::async_trait;
use std::sync::{atomic::AtomicUsize, Arc, Mutex};
use tokio::sync::mpsc::{channel, Sender};
use tokio::sync::Notify;
use tokio::task::JoinSet;
use tokio::time::{timeout, Duration};

#[derive(Clone)]
struct DispatcherRequest {
    ipl: PsiExecuteRequest,
    notify: Arc<Notify>,
    rsp: Arc<Mutex<Result<PsiExecuteResult, AppError>>>,
}

impl DispatcherRequest {
    fn new(req: &PsiExecuteRequest) -> Self {
        Self {
            ipl: req.clone(),
            notify: Arc::new(Notify::new()),
            rsp: Arc::new(Mutex::new(Err(AppError::new("not set".to_string())))),
        }
    }

    fn set_rsp(&mut self, rsp: Result<PsiExecuteResult, AppError>) {
        *self.rsp.lock().unwrap() = rsp;
        self.notify.notify_one();
    }
}

pub struct Batcher {
    chns: Vec<Sender<DispatcherRequest>>,
    exit_sig: Arc<Notify>,
    wait_group: JoinSet<()>,
    current: AtomicUsize,
    client: Arc<Client>,
}

#[async_trait]
impl PolicyImpl for Batcher {
    async fn execute(&self, req: &PsiExecuteRequest) -> Result<PsiExecuteResult, AppError> {
        let chn = &self.chns[self
            .current
            .fetch_add(1, std::sync::atomic::Ordering::Relaxed)
            % self.chns.len()];

        let req = self.client.encrypt_req(req)?;

        let dreq = DispatcherRequest::new(&req);
        let wait = dreq.notify.clone();
        let rsp = dreq.rsp.clone();
        let r = chn.send(dreq).await;
        if r.is_err() {
            return Err(AppError::new("send error".to_string()));
        }

        wait.notified().await;

        let rsp = rsp.lock().unwrap().clone();
        rsp
    }

    async fn shutdown(&mut self) {
        self.exit_sig.notify_waiters();
        while let Some(_) = self.wait_group.join_next().await {}
    }
}

impl Batcher {
    pub async fn new(
        client: Arc<Client>,
        workers: usize,
        duration_ms: usize,
        cache: usize,
        batch_size: usize,
    ) -> Result<Box<dyn PolicyImpl>, AppError>
    where
        Self: Sized,
    {
        let exit_sig = Arc::new(Notify::new());
        let mut chns = Vec::new();
        let mut wait_group = JoinSet::new();
        for _ in 0..workers {
            let exit_sig2 = exit_sig.clone();
            let (tx, mut rx) = channel::<DispatcherRequest>(cache);
            let client = client.clone();
            let duration = Duration::from_millis(duration_ms as u64);
            wait_group.spawn(async move {
                loop {
                    let mut exit = false;
                    let mut batch = Vec::<DispatcherRequest>::new();

                    tokio::select! {
                        _  = async  {exit_sig2.notified().await;} => {exit = true;},
                        _ = timeout(duration, async {
                            while let Some(item) = rx.recv().await {
                                batch.push(item);
                                if batch.len() == batch_size {
                                    break;
                                }
                            }
                        }) => {}
                    }

                    if (!batch.is_empty()) {
                        let batch_req = PsiExecuteRequest {
                            header: batch[0].ipl.header.clone(),
                            keys: batch
                                .clone()
                                .into_iter()
                                .flat_map(|req| req.ipl.keys)
                                .collect(),
                        };

                        // let res = client.psi_execute(&batch_req).await;
                        let res = client.psi_execute_without_encrypt(&batch_req).await;

                        if res.is_err() {
                            for req in &mut batch {
                                req.set_rsp(res.clone());
                            }
                        } else {
                            let res = match res {
                                Ok(res) => res,
                                Err(_) => panic!("unreachable"),
                            };

                            if !res.header.is_none() && res.header.as_ref().unwrap().code != 0 {
                                for req in &mut batch {
                                    req.set_rsp(Ok(res.clone()));
                                }
                            } else if res.header.is_none() {
                            } else {
                                let mut offset = 0;
                                for req in &mut batch {
                                    let rsp = PsiExecuteResult {
                                        header: back_header(&req.ipl.header),
                                        keys: res.keys[offset..req.ipl.keys.len() + offset].into(),
                                    };
                                    req.set_rsp(Ok(rsp));
                                    offset += req.ipl.keys.len();
                                }
                            }
                        }
                    }

                    if exit {
                        break;
                    }
                }
            });
            chns.push(tx)
        }

        Ok(Box::new(Batcher {
            chns: chns,
            exit_sig: exit_sig,
            current: AtomicUsize::new(0),
            wait_group,
            client: client,
        }))
    }
}
