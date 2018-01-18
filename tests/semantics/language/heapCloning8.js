function f(x) { return @ToObject(x); }

var x3 = f("three");
var x2 = f("two");
var x1 = f("one");

var __result1 = x1.length;
var __expect1 = 3;

var __result2 = x2.length;
var __expect2 = 3;

var __result3 = x3.length;
var __expect3 = 5;
