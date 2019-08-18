import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;

public class Monitor extends Thread
{
	OverAllData all=OverAllData.all;
	Connection conn = null;
	Statement stmt = null;
	SettingStruct setting=new SettingStruct();
	
	public void run()
	{
		//连接数据库
		ConnectMySql();
		try {
			while(true)
			{
				//更新设置
				GetSettings();
				//进行一次监控
				StartOneMonitor();
				stmt.close();
				sleep(1000);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	void ConnectMySql()
	{
		try
	    {
			// 打开链接
			System.out.println("开始连接数据库");
	    	// 注册 JDBC 驱动
		    Class.forName("com.mysql.cj.jdbc.Driver");
			conn = DriverManager.getConnection(all.SQLAddress,all.SQLAccount,all.SQLPassword);
			if(!conn.isClosed())
				System.out.println("连接数据库成功！");
			else
				System.out.println("连接数据库失败！");
		}
	    catch (Exception e)
	    {
	    	e.printStackTrace();
		}
	}
	
	//进行一次监控
	void StartOneMonitor()
	{
		try {
			//获取所有未监控过的值
			ResultSet allresult=GetAllNoMonitorValues();
			//对所有结果进行遍历扫描
			while(allresult.next())
			{
				//设置该条记录为已处理
				EditRecordToAlreadyMonitor(allresult.getString("time"),allresult.getString("timemillis"));
				//检测是否开启自动监控
				if(!setting.isIf_monitor())
					return;
				//如果发现不能通过
				String result_reason;
				if(!(result_reason=CheckIfOutOfRange(Integer.parseInt(allresult.getString("wendu")),Integer.parseInt(allresult.getString("shidu")),Integer.parseInt(allresult.getString("guangzhao")))).equals("OK"))
				{
					System.out.println("\n检测到异常值：time="+allresult.getString("time")+"，timemillis="+allresult.getString("timemillis")
					+"，温度="+allresult.getString("wendu")+
					"，湿度="+allresult.getString("shidu")+
					"，光照="+allresult.getString("guangzhao")+
					"，阈值："+setting.wendu_min+"-"+setting.wendu_max
					+"；"+setting.shidu_min+"-"+setting.shidu_max
					+"；"+setting.guangzhao_min+"-"+setting.guangzhao_max+"\n");
					String data1,data2,data3,data4,data5,data6;
					//根据具体原因选择具体的方法
					switch(result_reason)
					{
					case "wendu_low":
						data1="温度";
						data2=allresult.getString("wendu");
						data3="低于";
						data4=String.valueOf(setting.getWendu_min());
						data5="关闭";
						data6="遮光帘";
						SendMessage(setting.getPhone(),data1,data2,data3,data4,data5,data6);
						WriteControlMethod("B1");		//关闭遮光帘
						break;
					case "wendu_high":
						data1="温度";
						data2=allresult.getString("wendu");
						data3="高于";
						data4=String.valueOf(setting.getWendu_max());
						data5="打开";
						data6="遮光帘";
						SendMessage(setting.getPhone(),data1,data2,data3,data4,data5,data6);
						WriteControlMethod("B0");		//打开遮光帘
						break;
					case "shidu_low":
						data1="湿度";
						data2=allresult.getString("wendu");
						data3="低于";
						data4=String.valueOf(setting.getShidu_min());
						data5="打开";
						data6="灌溉";
						SendMessage(setting.getPhone(),data1,data2,data3,data4,data5,data6);
						WriteControlMethod("C0");		//开启灌溉
						break;
					case "shidu_high":
						data1="湿度";
						data2=allresult.getString("wendu");
						data3="高于";
						data4=String.valueOf(setting.getShidu_max());
						data5="打开";
						data6="排气扇";
						SendMessage(setting.getPhone(),data1,data2,data3,data4,data5,data6);
						WriteControlMethod("A0");		//开启排气扇
						break;
					case "gz_low":
						break;
					case "gz_high":
						break;
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	//获取当前系统时间
	SimpleDateFormat df=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	String GetCurrentTime()
	{
	       return df.format(GetTimeByLong());
	}
	//获取Long类型的时间，距离1970年1月1日起的毫秒数
	long GetTimeByLong()
	{
		return System.currentTimeMillis();
	}
	
	//获取setting设置
	void GetSettings()
	{
		try {
			stmt = conn.createStatement();
			ResultSet result=stmt.executeQuery("select * from userconfig");
			result.next();
			setting.setWendu_min(Integer.parseInt(result.getString("wendu_min")));
			setting.setWendu_max(Integer.parseInt(result.getString("wendu_max")));
			setting.setShidu_min(Integer.parseInt(result.getString("shidu_min")));
			setting.setShidu_max(Integer.parseInt(result.getString("shidu_max")));
			setting.setGuangzhao_min(Integer.parseInt(result.getString("gz_min")));
			setting.setGuangzhao_max(Integer.parseInt(result.getString("gz_max")));
			setting.setPhone(result.getString("phone"));
			setting.setIf_monitor(result.getString("monitor_switch").equals("1")?true:false);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	//获取所有未监控的值
	ResultSet GetAllNoMonitorValues()
	{
		try {
			stmt = conn.createStatement();
			return stmt.executeQuery("select * from `datamessage` where ifmonitor=0");
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	//修改某条记录为已设置
	void EditRecordToAlreadyMonitor(String time,String timemillis)
	{
		PreparedStatement pre=null;
		try {
			pre=conn.prepareStatement("update `datamessage` set ifmonitor = 1 where time=? and timemillis=?");
			pre.setString(1, time);
			pre.setString(2, timemillis);
			pre.executeUpdate();
			pre.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	//检测是否超出范围
	String CheckIfOutOfRange(int wendu,int shidu,int gz)
	{
		if(wendu<setting.getWendu_min())
			return "wendu_low";
		if(wendu>setting.getWendu_max())
			return "wendu_high";
		if(shidu<setting.getShidu_min())
			return "shidu_low";
		if(shidu>setting.getShidu_max())
			return "shidu_high";
		if(gz<setting.getGuangzhao_min())
			return "gz_low";
		if(gz>setting.getGuangzhao_max())
			return "gz_high";
		return "OK";
	}
	
	//写control入数据库
	void WriteControlMethod(String control_str)
	{
		PreparedStatement pre=null;
		try {
			pre=conn.prepareStatement("insert into `controlmessage` values(?,?,?,?)");
			pre.setString(1, GetCurrentTime());
			pre.setString(2, String.valueOf(GetTimeByLong()));
			pre.setString(3, control_str);
			pre.setString(4, "0");
			pre.executeUpdate();
			pre.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	//发送短信
	public void SendMessage(String phone_number,String data1,String data2,String data3,String data4,String data5,String data6)
	{
		String content="检测到您的["+data1+"]值出现异常：["+data2+"]，["+data3+"]您设置的临界值：["+data4+"]，系统已经自动为您["+data5+"]：["+data6+"]";
		String str="【TeleControl】"+content;
		System.out.println("时间："+GetCurrentTime()+"\n"+"发送短信：phone_number："+phone_number+"\n内容："+str);
		
	    StringBuffer httpArg = new StringBuffer();
	    httpArg.append("u=").append(all.message_username).append("&");
	    httpArg.append("p=").append(md5(all.message_password)).append("&");
	    httpArg.append("m=").append(phone_number).append("&");
	    httpArg.append("c=").append(encodeUrlString(str, "UTF-8"));
	    String result = request(all.httpUrl, httpArg.toString());
	    if(result.equals("0"))
	    	System.out.println("发送结果：成功\n");
	    else
	    	System.out.println("发送结果：失败，错误代码："+result+"\n");
	}
	
	public static String request(String httpUrl, String httpArg) {
        BufferedReader reader = null;
        String result = null;
        StringBuffer sbf = new StringBuffer();
        httpUrl = httpUrl + "?" + httpArg;
 
        try {
            URL url = new URL(httpUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            InputStream is = connection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String strRead = reader.readLine();
            if (strRead != null) {
                sbf.append(strRead);
                while ((strRead = reader.readLine()) != null) {
                    sbf.append("\n");
                    sbf.append(strRead);
                }
            }
            reader.close();
            result = sbf.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
 
    public static String md5(String plainText) {
        StringBuffer buf = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(plainText.getBytes());
            byte b[] = md.digest();
            int i;
            buf = new StringBuffer("");
            for (int offset = 0; offset < b.length; offset++) {
                i = b[offset];
                if (i < 0)
                    i += 256;
                if (i < 16)
                    buf.append("0");
                buf.append(Integer.toHexString(i));
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return buf.toString();
    }
 
    public static String encodeUrlString(String str, String charset) {
        String strret = null;
        if (str == null)
            return str;
        try {
            strret = java.net.URLEncoder.encode(str, charset);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return strret;
    }
}
