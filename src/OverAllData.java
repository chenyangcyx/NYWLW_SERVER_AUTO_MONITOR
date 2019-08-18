public class OverAllData
{
	public static OverAllData all=new OverAllData();
	
	final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";  
	public final String SQLAddress="jdbc:mysql://localhost:3306/nywlw?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=UTC";
	public final String SQLAccount="nywlw";
	public final String SQLPassword="nywlwnywlwnywlwnywlw";
    
    String message_username = "chenyangczyy"; //在短信宝注册的用户名
    String message_password = "czyy3291111"; 	//在短信宝注册的密码
    String httpUrl = "http://api.smsbao.com/sms";
}
