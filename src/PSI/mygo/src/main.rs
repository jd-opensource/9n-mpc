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

mod api;
mod encrypt;
mod execute;
mod front;
mod policy;

use crate::execute::ExecuteEngine;
use crate::front::{do_psi, AppStateDyn};
use crate::policy::{BatcherPolicyConf, PolicyConf};
use axum::{routing::post, Router};
use clap::Parser;
// use num_cpus;
use rand::distributions::Alphanumeric;
use rand::Rng;
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use tokio::net::TcpListener;
use tokio::signal;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

#[derive(Parser, Debug, Serialize, Deserialize)]
#[clap(author, version, about, long_about = None)]
struct Args {
    /// key
    #[clap(long)]
    key: String,

    /// id
    #[clap(long)]
    id: String,

    /// domain
    #[clap(long)]
    domain: String,

    /// host
    #[clap(long, default_value = "0.0.0.0:6325")]
    host: String,

    /// target
    #[clap(long)]
    target: String,

    /// remote
    #[clap(long)]
    remote: String,

    /// redis_address
    #[clap(long)]
    redis_address: String,

    /// redis_password
    #[clap(long, default_value = "")]
    redis_password: String,

    /// curve
    #[clap(long, default_value = "curve25519")]
    curve: String,

    /// psi_host
    #[clap(long, default_value = "0.0.0.0:6324")]
    psi_host: String,

    ///policy
    #[clap(long, default_value = "default")]
    policy: String,

    ///batcher-duration-ms
    #[clap(long, default_value = "10")]
    batcher_duration_ms: usize,

    ///batcher-cache
    #[clap(long, default_value = "10000")]
    batcher_cache: usize,

    ///batcher-batch-size
    #[clap(long, default_value = "1000")]
    batcher_batch_size: usize,

    ///batcher-workers
    #[clap(long, default_value = "8")]
    batcher_workers: usize,
}

#[tokio::main]
async fn main() {
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "mygo=debug".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    let mut args = Args::parse();

    //for debug
    if args.key.is_empty() {
        args.key = rand::thread_rng()
            .sample_iter(&Alphanumeric)
            .take(32)
            .map(char::from)
            .collect();
    }

    if !args.remote.starts_with("http") {
        args.remote = format!("http://{}", args.remote);
    }

    let engine = Arc::new(
        ExecuteEngine::new(
            args.key,
            args.curve,
            args.psi_host,
            args.remote,
            args.target,
            args.id,
            args.redis_address,
            args.redis_password,
            match args.policy.as_str() {
                "batcher" => PolicyConf::BatcherConf(BatcherPolicyConf {
                    // workers: num_cpus::get(),
                    workers: args.batcher_workers,
                    duration_ms: args.batcher_duration_ms,
                    cache: args.batcher_cache,
                    batch_size: args.batcher_batch_size,
                }),
                _ => PolicyConf::DefaultConf,
            },
        )
        .await
        .unwrap(),
    );

    let state = AppStateDyn { engine: engine };

    let app = Router::new()
        .route("/psi", post(do_psi))
        .with_state(state.clone());

    let listener = TcpListener::bind(args.host).await.unwrap();
    tracing::info!("listening on {}", listener.local_addr().unwrap());
    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal(state))
        .await
        .unwrap();
}

async fn shutdown_signal(state: AppStateDyn) {
    let ctrl_c = async {
        signal::ctrl_c()
            .await
            .expect("failed to install Ctrl+C handler");
    };

    #[cfg(unix)]
    let terminate = async {
        signal::unix::signal(signal::unix::SignalKind::terminate())
            .expect("failed to install signal handler")
            .recv()
            .await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {},
        _ = terminate => {},
    }

    state.engine.shutdown().await;
}

mod tests {
    use super::*;
    use crate::api::PsiExecuteRequest;
    use core::time;
    use policy::BatcherPolicyConf;
    use rand;
    use std::thread;
    use tokio;
    use tokio::sync::oneshot;
    use tokio::time::{sleep, Duration};

    #[tokio::test]
    async fn test_main() {
        let (shutdown, rx) = oneshot::channel::<()>();
        let alice = tokio::spawn(async {
            let key: String = rand::thread_rng()
                .sample_iter(&Alphanumeric)
                .take(32)
                .map(char::from)
                .collect();
            let curve = "curve25519".to_string();

            let mut engine = ExecuteEngine::new(
                key,
                curve,
                "127.0.0.1:6324".to_string(),
                "http://127.0.0.1:6325".to_string(),
                "bob".to_string(),
                "test".to_string(),
                "".to_string(),
                "".to_string(),
                PolicyConf::BatcherConf(BatcherPolicyConf {
                    workers: 8,
                    duration_ms: 10,
                    cache: 100,
                    batch_size: 100,
                }),
            )
            .await
            .unwrap();

            for i in 0..100 {
                let res = engine
                    .psi_execute(&PsiExecuteRequest {
                        header: None,
                        keys: vec!["1".into(), "2".into()],
                    })
                    .await
                    .unwrap();
            }

            let _ = shutdown.send(());
        });

        let bob = tokio::spawn(async {
            let key: String = rand::thread_rng()
                .sample_iter(&Alphanumeric)
                .take(32)
                .map(char::from)
                .collect();
            let curve = "curve25519".to_string();

            let engine = ExecuteEngine::new(
                key,
                curve,
                "127.0.0.1:6325".to_string(),
                "http://127.0.0.1:6324".to_string(),
                "alice".to_string(),
                "test".to_string(),
                "".to_string(),
                "".to_string(),
                PolicyConf::BatcherConf(BatcherPolicyConf {
                    workers: 8,
                    duration_ms: 10,
                    cache: 100,
                    batch_size: 100,
                }),
            )
            .await
            .unwrap();

            rx.await.ok();
        });

        alice.await.unwrap();
        bob.await.unwrap();
    }
}
