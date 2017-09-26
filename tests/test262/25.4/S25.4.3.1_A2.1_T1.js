// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
    Promise throws TypeError when 'this' is not Object
es6id: S25.4.3.1_A2.1_T1
author: Sam Mikes
description: Promise.call("non-object") throws TypeError
negative: TypeError
---*/

var __result1 = false;

try {
    Promise.call("non-object", function () {});
} catch (ex) {
    if (ex instanceof TypeError) {
        __result1 = true;
    }
}

var __expect1 = true;