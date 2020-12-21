-- Copyright 2020 The 9nFL Authors. All Rights Reserved.
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

local str = require "resty.string"
local redis = require "resty.redis"

local _M = {
    _VERSION = '0.0.1',
    cli = nil,
}

function _M.new(self)
    --lua idx start with 1

    local red = redis:new()
    red:set_timeouts(1000, 1000, 1000)

    --get host
    local host = ngx.var.redis_url
    if host == nil then
        local err = "fail to get redis ip!"
        ngx.log(ngx.ERR, err)
        return err
    end
    local port = ngx.var.redis_port
    if port  == nil then
        port = 6379
    end
    ngx.log(ngx.INFO, "select redis addr: ", host, port)
    local ok, err = red:connect(host, port)
    if not ok then
        ngx.log(ngx.ERR, "failed to connect: ", err)
        return err
    end
    self.cli = red
    return nil
end

function _M.get(self, key)
    local index = 0
    local err = nil
    local res
    repeat
        res, err = self.cli:get(key)
        if err ~= nil then
            index = index + 1
        else
            err = nil
            break
        end
    until (index > 2)
    if err ~= nil then
        ngx.log(ngx.ERR, "failed to get redis value ", err)
        return nil, err
    end
        
    if res == ngx.null or res == '' then
        err = "redis value is not exist"
        ngx.log(ngx.ERR, err)
        return nil, err
    end
    -- close 
    self.cli:close()
    return res, nil
end

return _M
