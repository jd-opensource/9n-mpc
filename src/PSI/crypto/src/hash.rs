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
use pyo3::exceptions::PyValueError;
use std::sync::Arc;

use pyo3::prelude::*;

use sha3::{
    digest::{ExtendableOutput, Update, XofReader},
    Shake256,
};

fn shake256_hash(data: &[u8], build: &mut BinaryBuilder) {
    let mut hasher = Shake256::default();
    hasher.update(data);
    let mut reader = hasher.finalize_xof();
    let mut hash_output = [0u8; 32];
    reader.read(&mut hash_output);

    build.append_value(&hash_output)
}

fn hash_impl(typ: &str, arr: &BinaryArray) -> BinaryArray {
    let fnc = match typ {
        "HASH_TYPE_SHAKE_256" => shake256_hash,
        &_ => panic!("no support hash type {}", typ),
    };

    let mut build = BinaryBuilder::new();
    for i in arr.iter() {
        fnc(i.unwrap(), &mut build);
    }

    return build.finish();
}

#[pyfunction]
pub(crate) fn hash(typ: &str, array: PyArrowType<ArrayData>) -> PyResult<PyArrowType<ArrayData>> {
    let array: ArrayData = array.0;
    let array: Arc<dyn Array> = make_array(array);
    let array: &BinaryArray = array
        .as_any()
        .downcast_ref()
        .ok_or_else(|| PyValueError::new_err("expected binary array"))?;

    let array: arrow::array::GenericByteArray<arrow::datatypes::GenericBinaryType<i32>> =
        hash_impl(typ, &array);
    Ok(PyArrowType(array.into_data()))
}

pub(crate) static SUPPORT_HASH: [&str; 1] = ["HASH_TYPE_SHAKE_256"];
