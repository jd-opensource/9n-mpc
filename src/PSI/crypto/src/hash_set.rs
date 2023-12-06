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
use std::collections::HashSet;

#[pyclass]
pub(crate) struct BytesHashSet {
    inner: HashSet<Vec<u8>>,
}

#[pymethods]
impl BytesHashSet {
    #[new]
    fn new() -> Self {
        BytesHashSet {
            inner: HashSet::new(),
        }
    }

    fn insert(&mut self, value: Vec<u8>) {
        self.inner.insert(value);
    }

    fn contains(&self, value: &[u8]) -> bool {
        self.inner.contains(value)
    }
}
