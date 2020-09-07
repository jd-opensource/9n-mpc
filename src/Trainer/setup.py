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
from setuptools import find_packages
from setuptools import setup

REQUIRED_PACKAGES = [
  'tensorflow >=1.15'
]

EXTENSION_NAME = 'fl_comm_libs/_fl.so'

setup(name='fl_comm_libs',
    version='0.1',
    description='comm libs for jdfl',
    author='JD Inc.',
    packages=find_packages('fl_comm_libs'),
    install_requires=REQUIRED_PACKAGES,
    include_package_data=True,
    package_data={
        'fl_comm_libs': [
            EXTENSION_NAME,
            'fl_comm_libs/*.sh'
        ],
    },
    license='Apache 2.0',
)
