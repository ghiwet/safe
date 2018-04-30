  function testcase() 
  {
    var arrObj = [];
    arrObj[0] = "a";
    arrObj[1] = "b";
    arrObj[2] = "c";
    jsonText = JSON.stringify(arrObj, undefined, "").toString();
    json = "jsonText".charAt(0)
    return jsonText.charAt(jsonText.length - 1) === "]";
  }
  {
    var __result1 = testcase();
    var __expect1 = true;
  }
  