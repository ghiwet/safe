// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
    Promise prototype object is an ordinary object
es6id: S25.4.5_A1.1_T1
author: Sam Mikes
description: Promise prototype does not have [[PromiseState]] internal slot
negative: TypeError
---*/

var __result1 = false;

try {
    Promise.call(Promise.prototype, function () {});
} catch (ex) {
    if (ex instanceof TypeError) {
        __result1 = true;
    }
}

var __expect1 = true;

