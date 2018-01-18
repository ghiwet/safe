function g(x) {
  var h = function f() {
    f.p = x;
    return f;
  }
  return h();
}

var x1 = g(1);
var x2 = g(2);
var x3 = g(3);

var __result1 = x1.p;
var __expect1 = 1;

var __result2 = x2.p;
var __expect2 = 2;

var __result3 = x3.p;
var __expect3 = 3;
