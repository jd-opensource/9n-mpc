# Copyright 2020 The 9nFL Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from setuptools import setup, find_packages
from setuptools.command.build_py import build_py
import os


class CustomBuildPy(build_py):
    def run(self):
        self.generate_proto()
        super().run()

    def generate_proto(self):
        os.system(
            "python3 -m grpc_tools.protoc -I. --python_out=. $( find . \\( -name \"*.proto\" \\) )")


setup(
    name="interconnection_psi",
    packages=find_packages(),
    cmdclass={
        'build_py': CustomBuildPy,
    },
)
