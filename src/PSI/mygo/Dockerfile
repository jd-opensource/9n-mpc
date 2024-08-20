FROM buildpack-deps:22.04

RUN set -ex && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
    llvm \
    protobuf-compiler \
    libprotobuf-dev \
    libclang-dev && \
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs >rustup.sh && \
    chmod +x rustup.sh && \
    ./rustup.sh -y && \
    cp ~/.cargo/bin/* /usr/local/bin/

COPY . /App/
WORKDIR /App

RUN cargo build --release && \
    mv target/release/mygo /App/mygo

ENTRYPOINT ["./mygo"]