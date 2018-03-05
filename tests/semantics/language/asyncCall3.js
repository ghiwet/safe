function bar () {
  var x = "OK";
  function foo () { __result = x; }
  @getLoop().push({ func: foo, args: []});
}

bar();

__result = "Error";
__expect = "OK";
