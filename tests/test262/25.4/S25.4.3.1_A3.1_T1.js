// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
    Promise throws TypeError when executor is not callable
es6id: S25.4.3.1_A3.1_T1
author: Sam Mikes
description: new Promise("not callable") throws TypeError
negative: TypeError
---*/

var __result1 = false;

try {
    new Promise("not callable");
} catch (ex) {
    if (ex instanceof TypeError) {
        __result1 = true;
    }
}

var __expect1 = true;