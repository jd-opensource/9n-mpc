#!/bin/sh

# source: https://users.rust-lang.org/t/use-tokio-tungstenite-with-rustls-instead-of-native-tls-for-secure-websockets/90130

openssl req -newkey rsa:2048 -nodes -subj "/C=FI/CN=vahid" -keyout key.pem -out key.csr

openssl x509 -signkey key.pem -in key.csr -req -days 365 -out cert.pem

openssl req -x509 -sha256 -nodes -subj "/C=FI/CN=vahid" -days 1825 -newkey rsa:2048 -keyout rootCA.key -out rootCA.crt

cat <<'EOF' >> localhost.ext
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
subjectAltName = @alt_names
[alt_names]
DNS.1 = mygo
EOF

openssl x509 -req -CA rootCA.crt -CAkey rootCA.key -in key.csr -out cert.pem -days 365 -CAcreateserial -extfile localhost.ext

cp rootCA.crt ca.pem
cp cert.pem server.pem
cp key.pem server.key
