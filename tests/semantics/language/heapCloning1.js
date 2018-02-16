function f(x) { return { p: x }; }

var x1 = f(1);
var x2 = f(2);
var x3 = f(3);

var __result1 = x1.p;
var __expect1 = 1;

var __result2 = x2.p;
var __expect2 = 2;

var __result3 = x3.p;
var __expect3 = 3;
