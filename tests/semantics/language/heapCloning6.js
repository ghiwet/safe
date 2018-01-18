function f(arr) {
  var sum = 0;
  for (var x in arr) {
    sum += arr[x];
  }
  return sum;
}

var __result1 = f([1, 2]);
var __expect1 = 3;

var __result2 = f([1, 2, 3]);
var __expect2 = 6;
