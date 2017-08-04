if(typeof console!=='object'){
    console= {        
        log: function(){},
        appDebug: function(){}       
    };
 }
 function Parent(){
     function Controller(){;}
     Controller.prototype= Object.prototype;
     return Controller;
 }
function Login(){
    function LoginController(){;}
    LoginController.prototype= new Parent();
    LoginController.prototype.login = function innerLogin(v){
       console.log("input ",v);
    }
    external = LoginController; 
    return LoginController;   
}

function main(){
    var LoginController= Login();
    var controller = new LoginController();
    external = controller; 
    controller.login(input);
    
}
main();
var controller = external;



