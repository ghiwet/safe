// Copyright 2014 Cubane Canada, Inc.  All rights reserved.
// See LICENSE for details.

/*---
info: >
    Promise is the Promise property of the global object
es6id: S25.4.3.1_A1.1_T1
author: Sam Mikes
description: Promise === global.Promise
includes: [fnGlobalObject.js]
---*/

var global = (function () { return this; })();

var __result1 = true;

if (Promise !== global.Promise) {
    // $ERROR("Expected Promise === global.Promise.");
    __result1 = false;
}

var __expect1 = true;
