FROM buildpack-deps:22.04

RUN set -ex && \
    sed -i 's/archive.ubuntu.com/mirrors.jd.com/g' /etc/apt/sources.list && \
    sed -i 's/security.ubuntu.com/mirrors.jd.com/g' /etc/apt/sources.list && \
    apt-get update && \
    apt-get upgrade -y --no-install-recommends && \
    apt-get install -y --no-install-recommends \
        cmake \
        python3 \
        bison \
        python3-dev \
        python3-pip \
        ninja-build \
        flex && \
    rm -rf /var/lib/apt/lists/*

RUN set -ex && \
    wget https://github.com/bazelbuild/bazel/releases/download/5.1.1/bazel-5.1.1-installer-linux-x86_64.sh && \
    bash bazel-5.1.1-installer-linux-x86_64.sh

RUN set -ex && \
    pip3 install maturin && \
    pip3 install ray==2.3.1 && \
    pip3 install pyarrow && \
    pip3 install grpcio-tools && \
    pip3 install pyOpenSSL && \
    pip3 install pandas && \
    pip3 install redis

RUN set -ex && \
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs >rustup.sh && \
    chmod +x rustup.sh && \
    ./rustup.sh -y && \
    cp ~/.cargo/bin/* /usr/local/bin/

COPY crypto /App/crypto
WORKDIR /App/crypto
RUN maturin build --release && \
    pip3 install --force-reinstall target/wheels/crypto-0.1.0-cp310-cp310-manylinux_2_34_x86_64.whl

COPY link_py /App/link_py
WORKDIR /App/link_py
RUN bazel build wheel --compilation_mode=opt --host_compilation_mode=opt && \
    pip3 install --force-reinstall bazel-bin/link_py-v0.1-py3-none-any.whl

COPY psi /App/psi
WORKDIR /App/psi
RUN python3 setup.py bdist_wheel && \
    pip3 install --force-reinstall  dist/interconnection_psi-0.0.0-py3-none-any.whl

COPY psi_actors.py /App/psi_actors.py

WORKDIR /App/

ENTRYPOINT ["python3","psi_actors.py"]