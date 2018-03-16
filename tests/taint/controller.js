function Login(){
    function LoginController(){;}
    LoginController.prototype= Object.prototype;
    LoginController.prototype.login = function innerLogin(v){
       //console.log("input ",v);
    }
    external = LoginController; 
    return LoginController;   
}
var input ={"login":login, "password":"gere"}
function main(){
    var LoginController= Login();
    var controller = new LoginController();
    external = controller; 
    controller.login(input);
    
}
main();
var controller = external;

__sinks = {
	"(info only)cotroller.login": external.login
};
Object.freeze(__sinks);

