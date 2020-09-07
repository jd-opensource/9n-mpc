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
# coding: utf-8


from functools import wraps
import logging


def cor_routine(func):
    @wraps(func)
    def next_count(*args, **kwargs):
        count_iterator = func(*args, **kwargs)
        next(count_iterator)
        return count_iterator

    return next_count


@cor_routine
def coordinator(cnt=0):
    while True:
        total = yield cnt
        cnt += 1


cor = coordinator()


def total(func):
    @wraps(func)
    def count_wrap(*args, **kwargs):
        call_cor_time = cor.send(1)
        logging.info('total count: %s' % call_cor_time)
        total_count = func(call_cor_time)
        return total_count

    return count_wrap


@total
def count(time):
    return time
