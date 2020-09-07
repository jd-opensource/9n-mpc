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
local redis = require "redis"

local _M = {
    _VERSION = '0.0.1',
}

function _M.split(input, sep)
    local arr = {}
    for str in string.gmatch(input, "([^"..sep.."]+)") do
        table.insert(arr, str)
    end
    return arr
end

function _M.set_name(self)
    local uri = ngx.var.uri
    ngx.log(ngx.INFO, "uri is : ", uri)
    if uri:find("^/") == nil  then
        ngx.log(ngx.ERR, "Url is invalid, uri : ", uri)
        return nil 
    end

    -- example uri "ip:port/service_name/method_name"
    local items = self.split(uri, "/")
    local service_name = items[1]
    local method_name = items[2]
    ngx.log(ngx.INFO, "Valid uri, service_name is : ", service_name,
                      ", method_name is : ", method_name)
    ngx.var.service_name = service_name
    ngx.var.method_name = method_name
end

function _M.get_uuid(self)
    local uuid = ngx.var.http_uuid
    if uuid == nil then
        ngx.log(ngx.ERR, "get uuid fail! uri is : ", ngx.var.uri)
        return nil
    end
    ngx.log(ngx.INFO, "get uuid success, uuid is ", uuid)
    return uuid
end

function _M.route(self)
    self:set_name()
    local uuid = self:get_uuid()
    if uuid == nil then
        ngx.log(ngx.ERR, "Get uuid fail!")
        return
    end

    local err = redis:new()
    if err ~= nil then
        ngx.log(ngx.ERR, "Init redis fail, reason is :", err)
        return
    end
    
    local remote_addr, err = redis:get(uuid)
    if remote_addr == nil or err ~= nil then    
        ngx.log(ngx.ERR, "Get uuid: ", uuid , 
                         " from redis fail!  reson is :", err)
    else
        ngx.log(ngx.INFO, "Get uuid from redis success, backend addr is : ", remote_addr)
        ngx.var.backend_addr = remote_addr 
        return
    end
end

return _M
