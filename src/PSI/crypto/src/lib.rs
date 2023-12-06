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

use pyo3::prelude::*;

mod curve;
mod hash;
mod hash_set;

#[pymodule]
fn crypto(_py: Python, m: &PyModule) -> PyResult<()> {
    m.add("SUPPORT_HASH", hash::SUPPORT_HASH)?;
    m.add_function(wrap_pyfunction!(hash::hash, m)?)?;

    m.add("SUPPORT_HASHTOCURVE", curve::SUPPORT_HASHTOCURVE)?;
    m.add_function(wrap_pyfunction!(curve::hash_to_curve, m)?)?;

    m.add("SUPPORT_CURVE", curve::SUPPORT_CURVE)?;
    m.add_class::<curve::Secret>()?;

    m.add(
        "SUPPORT_CURVE_POINT_OCTET",
        curve::SUPPORT_CURVE_POINT_OCTET,
    )?;
    m.add_function(wrap_pyfunction!(curve::point_octet_marshal, m)?)?;
    m.add_function(wrap_pyfunction!(curve::point_octet_unmarshal, m)?)?;

    m.add_class::<hash_set::BytesHashSet>()?;

    Ok(())
}
