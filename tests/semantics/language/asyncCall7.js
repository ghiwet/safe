function bar (msg) {
  @getLoop().push({ func: foo, args: [msg]});
}

function foo (x) { __result1 = x; }

bar("one");
bar("two");
bar("three");

__result1 = "";
__expect1 = "three";
