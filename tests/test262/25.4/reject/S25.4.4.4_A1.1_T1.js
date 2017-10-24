// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
   Promise.reject
es6id: S25.4.4.4_A1.1_T1
author: Sam Mikes
description: Promise.reject is a function
---*/

var __result1 = true;

if ((typeof Promise.reject) !== "function") {
    // $ERROR("Expected Promise.reject to be a function");
    __result1 = false;
}

var __expect1 = true;

var __result2 = true;

if (Promise.reject.length !== 1) {
    // $ERROR("Expected Promise.reject to be a function of one argument");
    __result2 = false;
}

var __expect2 = true;