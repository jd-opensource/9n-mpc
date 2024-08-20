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

use crate::front::err::AppError;

pub(crate) trait SecretFFI: Send + Sync {
    fn encrypt_self(&self, datas: &Vec<Vec<u8>>) -> Result<Vec<Vec<u8>>, AppError>;
    fn encrypt_peer(&self, datas: &Vec<Vec<u8>>) -> Result<Vec<Vec<u8>>, AppError>;
    fn new(key: &[u8]) -> Box<dyn SecretFFI>
    where
        Self: Sized;
}

use sha3::{
    digest::{ExtendableOutput, Update, XofReader},
    Shake256,
};

fn format_key(key: &[u8]) -> [u8; 32] {
    if key.len() == 32 {
        key.try_into().unwrap()
    } else {
        let mut hasher = Shake256::default();
        hasher.update(&key);
        let mut reader = hasher.finalize_xof();
        let mut hash_output = [0u8; 32];
        reader.read(&mut hash_output);

        hash_output
    }
}

mod curve25519_curve {
    use crate::encrypt::{format_key, SecretFFI};
    use crate::front::err::AppError;
    use curve25519_dalek::montgomery::MontgomeryPoint;
    use curve25519_dalek::scalar::Scalar;
    use sha3::{
        digest::{ExtendableOutput, Update, XofReader},
        Shake256,
    };

    pub(crate) struct Secret(Scalar);

    impl SecretFFI for Secret {
        fn encrypt_peer(&self, datas: &Vec<Vec<u8>>) -> Result<Vec<Vec<u8>>, AppError> {
            let mut result: Vec<Vec<u8>> = Vec::new();
            for data in datas {
                let point: MontgomeryPoint = MontgomeryPoint(data.as_slice().try_into()?);
                let r = point * self.0;
                result.push(r.as_bytes().into());
            }
            Ok(result)
        }

        fn encrypt_self(&self, datas: &Vec<Vec<u8>>) -> Result<Vec<Vec<u8>>, AppError> {
            let mut result: Vec<Vec<u8>> = Vec::new();
            for data in datas {
                let mut hasher = Shake256::default();
                hasher.update(&data);
                let mut reader = hasher.finalize_xof();
                let mut hash_output = [0u8; 32];
                reader.read(&mut hash_output);

                let point: MontgomeryPoint = MontgomeryPoint(hash_output);
                let r = point * self.0;
                result.push(r.as_bytes().into());
            }
            Ok(result)
        }

        fn new(key: &[u8]) -> Box<dyn SecretFFI> {
            Box::new(Secret(Scalar::from_bytes_mod_order(format_key(key))))
        }
    }
}

mod p256_curve {
    use crate::encrypt::{format_key, SecretFFI};
    use crate::front::err::AppError;
    use p256::{
        elliptic_curve::PublicKey, FieldBytes, NistP256, NonZeroScalar, ProjectivePoint, SecretKey,
    };
    use sha3::{
        digest::{ExtendableOutput, Update, XofReader},
        Shake256,
    };

    pub(crate) struct Secret(NonZeroScalar);

    impl SecretFFI for Secret {
        fn encrypt_self(&self, datas: &Vec<Vec<u8>>) -> Result<Vec<Vec<u8>>, AppError> {
            let mut result: Vec<Vec<u8>> = Vec::new();
            for data in datas {
                let mut hasher = Shake256::default();
                hasher.update(&data);
                let mut reader = hasher.finalize_xof();
                let mut hash_output = [0u8; 32];
                reader.read(&mut hash_output);

                let public_key = SecretKey::from_bytes(FieldBytes::from_slice(&hash_output))?;
                let public_key = NonZeroScalar::from(&public_key);
                let public_key = PublicKey::from_secret_scalar(&public_key);

                let public_point = ProjectivePoint::from(public_key);
                let secret_point = (public_point * self.0.as_ref()).to_affine();

                result.push(
                    PublicKey::<NistP256>::from_affine(secret_point)?
                        .to_sec1_bytes()
                        .as_ref()
                        .into(),
                );
            }
            Ok(result)
        }

        fn encrypt_peer(&self, datas: &Vec<Vec<u8>>) -> Result<Vec<Vec<u8>>, AppError> {
            let mut result: Vec<Vec<u8>> = Vec::new();
            for data in datas {
                let public_point = PublicKey::<NistP256>::from_sec1_bytes(data)?;
                let public_point = ProjectivePoint::from(public_point);
                let secret_point = (public_point * self.0.as_ref()).to_affine();

                result.push(
                    PublicKey::<NistP256>::from_affine(secret_point)?
                        .to_sec1_bytes()
                        .as_ref()
                        .into(),
                );
            }
            Ok(result)
        }

        fn new(key: &[u8]) -> Box<dyn SecretFFI>
        where
            Self: Sized,
        {
            let key = format_key(key);
            Box::new(Secret(NonZeroScalar::from(
                SecretKey::from_bytes(FieldBytes::from_slice(&key)).unwrap(),
            )))
        }
    }
}

// mod fourq_curve {
//     use crate::encrypt::SecretFFI;
//     use crate::front::err::AppError;
//     use fourq::point::Point;
//     use fourq::scalar::Scalar;

//     pub(crate) struct Secret(Scalar);

//     impl SecretFFI for Secret {
//         fn new(key: &[u8]) -> Box<dyn SecretFFI> {
//             Box::new(Secret(Scalar::new(key.try_into().unwrap())))
//         }

//         fn encrypt_self(&self, datas: &Vec<Vec<u8>>) -> Result<Vec<Vec<u8>>, AppError> {
//             let mut result: Vec<Vec<u8>> = Vec::new();
//             for data in datas {
//                 let point: Point = Point::from_hash(data);
//                 let r = point * self.0;
//                 let mut bytes = vec![0; 32];
//                 r.encode(&mut bytes);
//                 result.push(bytes);
//             }
//             Ok(result)
//         }
//         fn encrypt_peer(&self, datas: &Vec<Vec<u8>>) -> Result<Vec<Vec<u8>>, AppError> {
//             let mut result: Vec<Vec<u8>> = Vec::new();
//             for data in datas {
//                 let point = Point::decode(data);
//                 let r: Point = point * self.0;
//                 let mut bytes = vec![0; 32];
//                 r.encode(&mut bytes);
//                 result.push(bytes);
//             }
//             Ok(result)
//         }
//     }
// }

pub struct Curve {
    secret: Box<dyn SecretFFI>,
}

impl Curve {
    pub fn new(key: &[u8], curve: &str) -> Self {
        match curve {
            "curve25519" => Curve {
                secret: curve25519_curve::Secret::new(key),
            },
            // "fourq" => Curve {
            //     secret: fourq_curve::Secret::new(key),
            // },
            "p256" => Curve {
                secret: p256_curve::Secret::new(key),
            },
            _ => panic!("not support curve"),
        }
    }

    pub fn encrypt_self(&self, datas: &Vec<Vec<u8>>) -> Result<Vec<Vec<u8>>, AppError> {
        self.secret.encrypt_self(datas)
    }

    pub fn encrypt_peer(&self, datas: &Vec<Vec<u8>>) -> Result<Vec<Vec<u8>>, AppError> {
        self.secret.encrypt_peer(datas)
    }
}

mod tests {
    use super::*;

    // #[test]
    // fn test_fourq() {
    //     let curve1 = Curve::new(b"12345678901234567890123456789012", "fourq");
    //     let datas1 = vec![vec![1u8; 32]];

    //     let curve2 = Curve::new(b"32345678901234567890123456789012", "fourq");
    //     let datas2 = vec![vec![1u8; 32]];

    //     let datas1 = curve1.encrypt_self(&datas1).unwrap();
    //     let datas1 = curve2.encrypt_peer(&datas1).unwrap();

    //     let datas2 = curve2.encrypt_self(&datas2).unwrap();
    //     let datas2 = curve1.encrypt_peer(&datas2).unwrap();

    //     assert!(datas1 == datas2)
    // }

    #[test]
    fn test_curve25519() {
        let curve1 = Curve::new(b"12345678901234567890123456789012", "curve25519");
        let datas1 = vec![vec![1u8; 32]];

        let curve2 = Curve::new(b"32345678901234567890123456789012", "curve25519");
        let datas2 = vec![vec![1u8; 32]];

        let datas1 = curve1.encrypt_self(&datas1).unwrap();
        let datas1 = curve2.encrypt_peer(&datas1).unwrap();

        let datas2 = curve2.encrypt_self(&datas2).unwrap();
        let datas2 = curve1.encrypt_peer(&datas2).unwrap();

        assert!(datas1 == datas2)
    }

    #[test]
    fn test_p256() {
        let curve1 = Curve::new(b"12345678901234567890123456789012", "p256");
        let datas1 = vec![vec![1u8; 32]];

        let curve2 = Curve::new(b"32345678901234567890123456789012", "p256");
        let datas2 = vec![vec![1u8; 32]];

        let datas1 = curve1.encrypt_self(&datas1).unwrap();
        let datas1 = curve2.encrypt_peer(&datas1).unwrap();

        let datas2 = curve2.encrypt_self(&datas2).unwrap();
        let datas2 = curve1.encrypt_peer(&datas2).unwrap();

        assert!(datas1 == datas2)
    }

    use curve25519_dalek::montgomery::MontgomeryPoint;
    use curve25519_dalek::scalar::Scalar;
    use sha3::{
        digest::{ExtendableOutput, Update, XofReader},
        Shake256,
    };

    #[test]
    fn test_curve_process() {
        let jd_key = "12345678901234567890123456789012";
        let jd_device_id = "982945902";

        let xhs_key = "32345678901234567890123456789012";
        let xhs_device_id = "982945902";

        let jd_key = Scalar::from_bytes_mod_order(
            jd_key
                .as_bytes()
                .try_into()
                .expect("String should be 32 bytes long"),
        );

        let xhs_key = Scalar::from_bytes_mod_order(
            xhs_key
                .as_bytes()
                .try_into()
                .expect("String should be 32 bytes long"),
        );

        let mut hasher = Shake256::default();
        hasher.update(jd_device_id.as_bytes());
        let mut reader = hasher.finalize_xof();
        let mut hash_output = [0u8; 32];
        reader.read(&mut hash_output);
        let jd_device_id = hash_output;
        print!("{:?}\n", jd_device_id);

        let jd_device_id = MontgomeryPoint(jd_device_id);
        print!("{:?}\n", jd_device_id.to_bytes());

        let mut hasher = Shake256::default();
        hasher.update(xhs_device_id.as_bytes());
        let mut reader = hasher.finalize_xof();
        let mut hash_output = [0u8; 32];
        reader.read(&mut hash_output);
        let xhs_device_id = hash_output;
        print!("{:?}\n", xhs_device_id);

        let xhs_device_id = MontgomeryPoint(xhs_device_id);
        print!("{:?}\n", xhs_device_id.to_bytes());

        let jd_pub_key = jd_device_id * jd_key;
        print!("{:?}\n", jd_pub_key.to_bytes());

        let xhs_pub_key = xhs_device_id * xhs_key;
        print!("{:?}\n", xhs_pub_key.to_bytes());

        let xhs_shared_key = jd_pub_key * xhs_key;
        print!("{:?}\n", xhs_shared_key.to_bytes());

        let jd_shared_key = xhs_pub_key * jd_key;
        print!("{:?}\n", jd_shared_key.to_bytes());

        assert!(xhs_shared_key == jd_shared_key);
    }

    use core::borrow::Borrow;
    use p256::{
        ecdh::{diffie_hellman, SharedSecret},
        elliptic_curve::PublicKey,
        FieldBytes, NistP256, NonZeroScalar, ProjectivePoint, SecretKey,
    };

    pub fn encrypt_self(
        secret_key: &SecretKey,
        public_key: &PublicKey<NistP256>,
    ) -> Result<PublicKey<NistP256>, p256::elliptic_curve::Error> {
        let public_point = ProjectivePoint::from(public_key);
        let secret_key = NonZeroScalar::from(secret_key);
        let secret_point = (public_point * secret_key.borrow().as_ref()).to_affine();
        PublicKey::from_affine(secret_point)
    }

    pub fn encrypt_peer(
        secret_key: &SecretKey,
        public_key: &PublicKey<NistP256>,
    ) -> Result<SharedSecret, p256::elliptic_curve::Error> {
        let public_point = ProjectivePoint::from(public_key);
        let secret_key = NonZeroScalar::from(secret_key);

        Ok(diffie_hellman(secret_key, public_point.to_affine()))
    }

    #[test]
    fn test_p256_curve_process() {
        let jd_key = "12345678901234567890123456789012";
        let jd_device_id = "982945902";

        let xhs_key = "32345678901234567890123456789012";
        let xhs_device_id = "982945902";

        let jd_key = SecretKey::from_bytes(FieldBytes::from_slice(jd_key.as_bytes())).unwrap();

        let xhs_key = SecretKey::from_bytes(FieldBytes::from_slice(xhs_key.as_bytes())).unwrap();

        let mut hasher = Shake256::default();
        hasher.update(jd_device_id.as_bytes());
        let mut reader = hasher.finalize_xof();
        let mut hash_output = [0u8; 32];
        reader.read(&mut hash_output);
        let jd_device_id = hash_output;
        print!("{:?}\n", jd_device_id);

        let jd_device_id = SecretKey::from_bytes(FieldBytes::from_slice(&jd_device_id)).unwrap();
        let jd_device_id = NonZeroScalar::from(&jd_device_id);
        let jd_device_id = PublicKey::from_secret_scalar(&jd_device_id);
        print!("{:?}\n", jd_device_id);

        let mut hasher = Shake256::default();
        hasher.update(xhs_device_id.as_bytes());
        let mut reader = hasher.finalize_xof();
        let mut hash_output = [0u8; 32];
        reader.read(&mut hash_output);
        let xhs_device_id = hash_output;
        print!("{:?}\n", xhs_device_id);

        let xhs_device_id = SecretKey::from_bytes(FieldBytes::from_slice(&xhs_device_id)).unwrap();
        let xhs_device_id = NonZeroScalar::from(&xhs_device_id);
        let xhs_device_id = PublicKey::from_secret_scalar(&xhs_device_id);
        print!("{:?}\n", xhs_device_id);

        let jd_pub_key = encrypt_self(&jd_key, &jd_device_id).unwrap();
        print!("{:?}\n", jd_pub_key);

        let xhs_pub_key = encrypt_self(&xhs_key, &xhs_device_id).unwrap();
        print!("{:?}\n", xhs_pub_key);

        let xhs_shared_key = encrypt_peer(&xhs_key, &jd_pub_key).unwrap();
        print!("{:?}\n", xhs_shared_key.raw_secret_bytes());

        let jd_shared_key = encrypt_peer(&jd_key, &xhs_pub_key).unwrap();
        print!("{:?}\n", jd_shared_key.raw_secret_bytes());

        assert!(xhs_shared_key.raw_secret_bytes() == jd_shared_key.raw_secret_bytes());
    }
}
