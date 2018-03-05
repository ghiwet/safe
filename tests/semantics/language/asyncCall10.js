var sequence = [];

function checkSequence (arr) {
  __result1 = true;
  for (var i = 0; i < arr.length; i++) {
    if (arr[i] !== i + 1) {
      __result1 = false;
    }
  }
}

function foo (x) { sequence.push(x[0]); }

for (var i = 1; i <= 2; i++) {
  var args = [i];
  @getLoop().push({ func: foo, args: [args]});
}

@getLoop().push({ func: checkSequence, args: [sequence]});

__result1 = false;
__expect1 = true;
