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

use arrow::array::{make_array, Array, ArrayData, BinaryArray, BinaryBuilder};
use arrow::pyarrow::PyArrowType;
use curve25519_dalek::montgomery::MontgomeryPoint;
use curve25519_dalek::scalar::Scalar;
use pyo3::exceptions::PyValueError;
use pyo3::prelude::*;
use rand::rngs::StdRng;
use rand::{RngCore, SeedableRng};
use std::sync::Arc;

#[pyclass(module = "crypto.curve", name = "Curve")]
pub(crate) struct Secret(Scalar);

impl Secret {
    fn encrypt_impl(&self, data: &[u8], build: &mut BinaryBuilder) {
        build.append_value(
            (MontgomeryPoint(data.try_into().expect("encrypt accpet 32 bytes pubkey")) * self.0)
                .as_bytes(),
        );
    }

    fn diffie_hellman_impl(&self, data: &[u8], build: &mut BinaryBuilder) {
        build.append_value(
            (MontgomeryPoint(
                data.try_into()
                    .expect("diffie_hellman accpet 32 bytes pubkey"),
            ) * self.0)
                .as_bytes(),
        );
    }

    fn run_impl(
        &self,
        func: fn(s: &Secret, data: &[u8], build: &mut BinaryBuilder),
        array: PyArrowType<ArrayData>,
    ) -> PyResult<PyArrowType<ArrayData>> {
        let array: ArrayData = array.0;
        let array: Arc<dyn Array> = make_array(array);
        let array: &BinaryArray = array
            .as_any()
            .downcast_ref()
            .ok_or_else(|| PyValueError::new_err("expected binary array"))?;

        let mut build = BinaryBuilder::new();
        for i in array.iter() {
            func(self, i.unwrap(), &mut build);
        }

        Ok(PyArrowType(build.finish().into_data()))
    }
}

#[pymethods]
impl Secret {
    #[new]
    #[pyo3(signature = (typ,key))]
    fn pynew(typ: &str, key: Option<[u8; 32]>) -> PyResult<Self> {
        if !SUPPORT_CURVE.contains(&typ) {
            panic!("no support curve type {}", typ)
        }

        Ok(Self(Scalar::from_bytes_mod_order(key.unwrap_or_else(
            || {
                let mut bytes: [u8; 32] = [0; 32];
                StdRng::from_entropy().fill_bytes(&mut bytes);
                bytes
            },
        ))))
    }

    #[pyo3(text_signature = "($self, array)")]
    fn encrypt(&self, array: PyArrowType<ArrayData>) -> PyResult<PyArrowType<ArrayData>> {
        self.run_impl(Secret::encrypt_impl, array)
    }

    #[pyo3(text_signature = "($self, array)")]
    fn diffie_hellman(&self, array: PyArrowType<ArrayData>) -> PyResult<PyArrowType<ArrayData>> {
        self.run_impl(Secret::diffie_hellman_impl, array)
    }
}

#[pyfunction]
pub(crate) fn hash_to_curve(
    typ: &str,
    array: PyArrowType<ArrayData>,
) -> PyResult<PyArrowType<ArrayData>> {
    if !SUPPORT_HASHTOCURVE.contains(&typ) {
        panic!("no support hash_to_curve type {}", typ)
    }

    Ok(array)
}

#[pyfunction]
pub(crate) fn point_octet_marshal(
    typ: &str,
    array: PyArrowType<ArrayData>,
) -> PyResult<PyArrowType<ArrayData>> {
    if !SUPPORT_CURVE_POINT_OCTET.contains(&typ) {
        panic!("no support hash_to_curve type {}", typ)
    }

    Ok(array)
}

#[pyfunction]
pub(crate) fn point_octet_unmarshal(
    typ: &str,
    data: &[u8],
    count: usize,
) -> PyResult<PyArrowType<ArrayData>> {
    if !SUPPORT_CURVE_POINT_OCTET.contains(&typ) {
        panic!("no support hash_to_curve type {}", typ)
    }

    let mut build = BinaryBuilder::new();
    let block_size = data.len() / count;

    for i in 0..count {
        build.append_value(&data[i * block_size..i * block_size + block_size])
    }

    Ok(PyArrowType(build.finish().into_data()))
}

pub(crate) static SUPPORT_CURVE: [&str; 1] = ["CURVE_TYPE_CURVE25519"];
pub(crate) static SUPPORT_HASHTOCURVE: [&str; 1] =
    ["HASH_TO_CURVE_STRATEGY_DIRECT_HASH_AS_POINT_X"];
pub(crate) static SUPPORT_CURVE_POINT_OCTET: [&str; 1] = ["POINT_OCTET_FORMAT_UNCOMPRESSED"];
