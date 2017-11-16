// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
    catch(arg) is equivalent to then(undefined, arg)
es6id: S25.4.5.1_A3.1_T1
author: Sam Mikes
description: catch is implemented in terms of then
---*/

var obj = {};
var __result1 = true;
var __result2 = false;

var p = Promise.resolve(obj);

p.catch(function () {
    //$ERROR("Should not be called - promise is fulfilled");
    __result1 = false;
}).then(function (arg) {
    if (arg === obj) {
        //$ERROR("Expected promise to be fulfilled with obj, got " + arg);
        __result2 = true;
    }
})

var __expect1 = true;
var __expect2 = true;