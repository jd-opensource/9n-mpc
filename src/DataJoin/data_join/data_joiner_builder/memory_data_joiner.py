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

import logging

from DataJoin.data_join.data_joiner_builder.data_joiner import DataJoiner


class MemoryDataJoiner(DataJoiner):
    def __init__(self, data_joiner_options,
                 raw_data_options,
                 data_block_dir,
                 data_source_name,
                 raw_data_dir,
                 partition_id,
                 mode, queue):
        super(MemoryDataJoiner, self).__init__(data_joiner_options,
                                               raw_data_options,
                                               data_block_dir,
                                               data_source_name,
                                               raw_data_dir, partition_id,
                                               mode, queue)

    @classmethod
    def joiner_name(cls):
        return 'MEMORY_JOINER'

    def _data_joiner_algo(self):
        item_dict = self._follower_visitor.item_dict
        while True:
            if self.is_data_joiner_finished():
                logging.warning("join example for partition %d by %s has finished",
                                self._partition_id, self.joiner_name())
                break
            if not self._leader_visitor.empty():
                lite_example_ids = self._leader_visitor.get()
                if len(lite_example_ids.example_id) > 0:
                    for example_id in lite_example_ids.example_id:
                        if example_id in item_dict:
                            maker = self._acquire_data_block_maker(True)
                            maker.save(item_dict[example_id].record, example_id,
                                       item_dict[example_id].event_time)
                            if maker.is_data_block_exceed_threshold():
                                yield self._data_join_finalizer(lite_example_ids.finished)
                    if lite_example_ids.finished:
                        if self._acquire_data_block_maker(False) is not None:
                            yield self._data_join_finalizer(lite_example_ids.finished)

                else:
                    if lite_example_ids.finished:
                        if self._acquire_data_block_maker(False) is not None:
                            yield self._data_join_finalizer(lite_example_ids.finished)

            if self._acquire_data_block_maker(False) is not None and \
                    self._data_block_finalizer_if_time_span():
                yield self._data_join_finalizer(lite_example_ids.finished)

    def _data_join_finalizer(self, is_data_joiner_finished):
        data_block_meta = super(MemoryDataJoiner, self)._data_join_finalizer(is_data_joiner_finished)
        return data_block_meta
