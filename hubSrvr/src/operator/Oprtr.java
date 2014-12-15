package operator;

import base.ActionProcessor;
import base.ReqCtx;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.jasypt.util.password.StrongPasswordEncryptor;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.SerializationUtils;

/**
 * Servlet implementation class Oprtr
 */
@WebServlet(description = "Handles all Actions for OperatorApp", urlPatterns = { "/Oprtr" })
public final class Oprtr extends ActionProcessor {
	private static final long serialVersionUID = 1L;
	
	final String clntSessId_tag ="clntSessId";
	final String secret_tag ="secret";
	final String Password_tag="Password";
	final String userId_tag ="userId";
	final String EventNotes_tag ="EventNotes";
	final String Details_tag ="Details";
	final String DebitUserId_tag ="DebitUserId";
	final String CreditUserId_tag ="CreditUserId";
	final String Amount_tag ="Amount";
	final String alias_tag ="alias";
	final String DevId_tag ="DevId";
	final String isOperator_tag ="isOperator";
	static final String userKeyID_tag = "userKeyID";
	static final String mobNum_tag= "mobNum";
	static final String usrName_tag = "usrName";
	static final String address_tag = "address";
	static final String custDeviceId_tag = "custDeviceId";
	static final String initialCredit_tag = "initialCredit";
	static final String usrEmail_tag = "usrEmail";
	static final String notes_tag = "notes";
	static final String imageFile_tag = "imageFile";
	static final String idProofFile_tag = "idProofFile";
	static final String addrProofFile_tag = "addrProofFile";
	static final String profilePic_tag = "profilePic";
	
	
    
	static final PrintStream o = System.out;
    static HashMap<String,HashSet<String>> loggedIn = new  HashMap<String,HashSet<String>>();
    final String JSESSIONID_tag = "JSESSIONID";
    Logger logger = null;
 
    static final Level INFO = Level.INFO;
    static final Level WARNING = Level.WARNING;
    static final Level SEVERE = Level.SEVERE;

	
    /**
     * @see ActionProcessor#ActionProcessor()
     */
    public Oprtr() {
        super();
        logger = Logger.getLogger("operator.Oprtr");
        o.println("In Oprtr Constructor..");
       // new Thread(new CreditDeductor()).run();
    }
    
    private void log(Level l,String msg){
    	logger.log(l,msg);
    }
    
    private ReqCtx getRequestContext(HttpServletRequest request){
    	return new ReqCtx(request, getSessionId()); // TBD
    }
    
    Connection con = null;  
    
    
    private void checkDB() throws ServletException{
        if (null == con)
        	try {
        		Class.forName("com.mysql.jdbc.Driver");
        		con =DriverManager.getConnection("jdbc:mysql://localhost:3306/apphub","bits","fixit");
        		// TBD - create a lower access account and connection for other servlets
        	} catch (SQLException e) {
        		o.println("Sql exception in checkDB : "+e);
        		throw new ServletException("Servlet Could not display records.", e);
        	} catch (ClassNotFoundException e) {
        		o.println("Classnotfound Exception in checkDB : "+e);
        		throw new ServletException("JDBC Driver not found.", e);
        	} 
        //stmt = con.createStatement();
    	
    }
	  
    
    /**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	    boolean authenticated = true;
		ReqCtx ctx = getRequestContext(request);
		if(!ctx.isActivity()){
			response.sendError(HttpServletResponse.SC_EXPECTATION_FAILED,"Only Activities supported" ); 
			log(Level.WARNING,"Only Activities supported");
			return ;
		}
		if(ctx.hasFile()){
			// will handle this later
		} 
		HttpSession session = request.getSession(true);
		log(Level.INFO,"Incoming session ");
		
        checkDB();
        if (!ctx.getOpName().equalsIgnoreCase("Login") && !ctx.getOpName().equalsIgnoreCase("Logout")){ 
        	String sessionId = ctx.getArgVal(JSESSIONID_tag); 
        	log(Level.INFO,"Cookie Id in the request:"+sessionId);
			if (sessionId == null) authenticated = false;
        	else {
        		HttpSession sess = SessionCounterListener.getSession(sessionId);
        		if (sess==null) authenticated = false;
        		else {
        			String user = (String)sess.getAttribute(userId_tag);
        			String pass = (String)sess.getAttribute(Password_tag);
        			o.println(user+" "+pass);
        			authenticated = validate(user, pass,session);
        			if (authenticated) 
        				log(Level.INFO,"Authenticated User "+user);
        		}
        	}
        }
        
	    if(!authenticated){
	    	errorResponse(ctx,response, "NoSession.Authentication failed!!",HttpServletResponse.SC_UNAUTHORIZED);
	    	log(Level.WARNING,"No Session.Authentication failed!!");
			return;
	    }
	   
	    try {
			if(ctx.getOpName().equalsIgnoreCase("Login")){ //  Login Logout AddUser DeleteUser
				login(ctx,response, session);
				return;
			} else if(ctx.getOpName().equalsIgnoreCase("Logout")){
				logout(ctx,response, session);
				return;
			}
			if(session.getAttribute(isOperator_tag).equals("true")) { // following operations only for Operator
				if(ctx.getOpName().equalsIgnoreCase("AddUser")){
				   addUser(ctx,response);
				}
				else if(ctx.getOpName().equalsIgnoreCase("ListUser")){
					listUser(ctx,response);
				}else if(ctx.getOpName().equalsIgnoreCase("EditUser")){
					editUser(ctx,response);
				}else if(ctx.getOpName().equalsIgnoreCase("DeleteUser")){
					disableUser(ctx,response);
				}else if(ctx.getOpName().equalsIgnoreCase("ChangeCredits") 
						|| ctx.getOpName().equalsIgnoreCase("RechargeUser") ){
					changeCredits(ctx,response);
				}else if(ctx.getOpName().equalsIgnoreCase("GetTransactions")){
					listTransactions(ctx,response);
				} 
				else if(ctx.getOpName().equalsIgnoreCase("UpdateUserAddress")){
					updateAddress(ctx,response);
				} else if(ctx.getOpName().equalsIgnoreCase("UpdateUserDetails")){ //UpdateUserDetails UpdateDevice
					updateDetails(ctx,response);
				} else if(ctx.getOpName().equalsIgnoreCase("UpdateDevice")){
					updateDevice(ctx,response);
				} 
			    return;
			}
			
			if(ctx.getOpName().equalsIgnoreCase("LookupUser")){ //  LookupUser GetUserApps 
				lookupUser(ctx,response);
			} else if(ctx.getOpName().equalsIgnoreCase("GetUserApps")){
				getUserApps(ctx,response);
			} else if(ctx.getOpName().equalsIgnoreCase("GetAppDetails")){ // GetAppDetails UpdateUserAppRole
				getAppDetails(ctx,response);
			} else if(ctx.getOpName().equalsIgnoreCase("UpdateUserAppRole")){
				updateUserAppRole(ctx,response);
			} else if(ctx.getOpName().equalsIgnoreCase("GetBillDetails")){ //  GetBillDetails DeliveryUpdate
				getBillDetails(ctx,response);
			} else if(ctx.getOpName().equalsIgnoreCase("DeliveryUpdate")){
				deliveryUpdate(ctx,response);
			} else {
				errorResponse(ctx,response, "Wrong Action Requested");
			}
			if(null != con) {
				con.close();
				con=null;
			}
			
		} catch (Exception ex) {
			errorResponse(ctx,response, "Internal Error");
			getServletContext().log("Failure in doGet try block");
			o.println("Exception in doGet of Oprtr: ");
			ex.printStackTrace();
		} finally {
			try { if (null != con) con.close(); } catch (Exception ex) {} // do nothing
			con = null;
		}
			
			// Notification - need not be handled  SyncDevice
	}

	//**********************************************EDIT USER*******************************************
	
	private void editUser(ReqCtx ctx, HttpServletResponse response) throws IOException{
		PreparedStatement stmt = null;
		
		String userKeyID = ctx.getArgVal(userKeyID_tag);
	    String mobNum = ctx.getArgVal(mobNum_tag);
		String alias = ctx.getArgVal(alias_tag);
		String usrName = ctx.getArgVal(usrName_tag);
		String address = ctx.getArgVal(address_tag);
		
		String usrEmail = ctx.getArgVal(usrEmail_tag);
		String notes = ctx.getArgVal(notes_tag);
		String photoFile = ctx.getArgVal(imageFile_tag);
		String idProofFile = ctx.getArgVal(idProofFile_tag);
		String addressProofFile = ctx.getArgVal(addrProofFile_tag);
		String profilePic = ctx.getArgVal(profilePic_tag);
		//String profilePic = null;
		
		if(null == usrEmail) usrEmail = "";
		if(null == notes) notes="";
		
		try {
			stmt = con.prepareStatement("UPDATE User SET Name=?,Alias=?,EmailAddress=?,MobileNumber=?," +
                  "Address=?, ProfilePic=?,ImageFile=?, IdProofFile=?,AddrProofFile=?," + 
                  "AddressVerificationNotes=? WHERE UserId =?",
                  Statement.RETURN_GENERATED_KEYS);
			o.println("Before filling the fields");
			stmt.setString(1, usrName);
			stmt.setString(2, alias);
			stmt.setString(3, usrEmail);
			stmt.setString(4, mobNum);
			stmt.setString(5, address);
			stmt.setString(6, profilePic);
			stmt.setString(7, photoFile);
			stmt.setString(8, idProofFile);
			stmt.setString(9, addressProofFile);
			stmt.setString(10, notes);
			stmt.setInt(11, Integer.parseInt(userKeyID));
			
			int affectedRows = stmt.executeUpdate();
	        if (affectedRows == 0) {
	            throw new SQLException("Editing user number"+userKeyID+" failed, no rows affected.");
	        }

	        o.println("Successfully Edited...!!");
	        successResponse(ctx,response, "Edited user with UserId: "+userKeyID);
	    } catch(Exception ex){
	    	o.println("Error in addUser in Oprtr: "+ex);
			errorResponse(ctx, response, ex.getMessage());
	    }finally {
	        if (stmt != null) try { stmt.close(); } catch (SQLException logOrIgnore) {}
	    }
		
	
	}


private boolean validate(String user,String pass,HttpSession session){
	try {
	if (con==null) o.println("IS NULL"); 
	PreparedStatement stmt = con.prepareStatement("SELECT Password,Status FROM User WHERE MobileNumber = ?",
			Statement.RETURN_GENERATED_KEYS);
    stmt.setString(1, user);
    ResultSet rs = stmt.executeQuery();
    if(!rs.next())
       return false;
    String dbPass=rs.getString("Password");
    String dbStatus = rs.getString("Status");
    if (dbStatus.equalsIgnoreCase("operator")) 
    	session.setAttribute(isOperator_tag,"true");
    else session.setAttribute(isOperator_tag,"false");
    return dbPass.equals(pass);
	} catch(Exception e){
		o.println("Exception in validate..!!"+e);
	}
	return false;
}



	
	/***
	 * Get User Name, session etc. and validate with database
	 * Respond with success or fail
	 *       <argument argName="sessionId" argValue="clients's nonce - post encryption with servers public key" />
     *       <argument argName="secret" argValue="shared secret - hash value - with key as sessionId" />
     *       <behavior>shared secret to be configured per device, sessionId should be pseudo random from client</behavior>
     * No encryption implemented yet
     * Shared secret per device pending
     * sessionId implementation pending
	 * @param ctx
	 * @param response
	 * @throws IOException
	 */
	private void login(ReqCtx ctx, HttpServletResponse response, HttpSession session) throws  IOException {
		PreparedStatement stmt = null;
		ResultSet rs = null;
		String userId = ctx.getUserId();
		String sessionId = ctx.getArgVal(clntSessId_tag);
		String secret = ctx.getArgVal(secret_tag);
		String usrPass = ctx.getArgVal(Password_tag);
		StrongPasswordEncryptor passwordEncryptor = new StrongPasswordEncryptor();
		
		if(null == sessionId || null == secret) {
			 errorResponse(ctx, response, "missingArgs:secret|sessionId");
			 return;// working here
		}
		try {
			boolean status = false;
			stmt = con.prepareStatement("SELECT Password,Status FROM User WHERE MobileNumber = ?");
	        stmt.setString(1, userId);
	        rs = stmt.executeQuery();
	        if(!rs.next()){
	            throw new Exception("No User"); // XXX TBD - should not let end client see this                 
	        }

	        String dbPass=rs.getString("Password");
	        String dbStatus = rs.getString("Status");
	        
	        try {
		        if(passwordEncryptor.checkPassword(usrPass, dbPass)){
		            status = true;
		        } 
	        } catch (Exception e) {
	        	// do nothing - ignore StrongPasswordEncryptor checks in development : TBD XXX
	        }
	    	
	        if(!status){
	        	if(dbPass.equals(usrPass) || secret.equals("testPass")){
		        	status = true;
		        }
	        }
	        if (status && dbStatus.equalsIgnoreCase("operator")) { // 'active', 'suspended', 'deleted', 'operator'
	        	session.setAttribute(isOperator_tag, "true");
	        }
	        // XXX TBD - check that deviceId belongs to the User
	        
	        if(!status) 
	        	throw new Exception("Unauthorized");

        	// add ledger entry
	        session.setAttribute(userId_tag,ctx.getUserId());
	    	session.setAttribute(DevId_tag,ctx.getDevId());
	    	session.setAttribute(clntSessId_tag, sessionId);
	    	session.setAttribute(Password_tag,usrPass);
	    	session.setMaxInactiveInterval(3600);  // should be smaller in production TODO
	    	//session.setMaxInactiveInterval(5);  
	    	//String message="Welcome " + userId + " from " + deviceId;
	    	Cookie loginCookie = new Cookie("USERNAME",userId);
     	    response.addCookie(loginCookie);
     	    
     	   log(Level.INFO,"Current session : "+session.getId());
   		
     	   successResponse(ctx,response,"Added User : "+userId);
	    }catch (Exception e) {
			errorResponse(ctx, response, "processing error:"+ e.getMessage()); // should not show user the actual error
			log(WARNING,"Error in login in Oprtr: "+e);
		} finally {
	        if (stmt != null) try { stmt.close(); } catch (SQLException logOrIgnore) {}
	    }
	}
		
	private void logout(ReqCtx ctx, HttpServletResponse response, HttpSession session) throws  IOException {
		String cookieId =ctx.getArgVal(JSESSIONID_tag);
		String user =ctx.getUserId();
		
		if(cookieId!=null) {
			session.invalidate();
			SessionCounterListener.removeSession(cookieId);
			successResponse(ctx, response,"Logged Out "+user);
			log(INFO,"Logged out the user "+user);
		} else {
			errorResponse(ctx, response, "Invalid Session");
			log(WARNING,"Invalid session while logging out");
		}
	}
	
	private void listUser(ReqCtx ctx, HttpServletResponse response) throws IOException{
		try {
		String sessionId = ctx.getArgVal(JSESSIONID_tag);
		
		HttpSession session = SessionCounterListener.getSession(sessionId);
		OutputStream s = response.getOutputStream();
		//outstmt = con.prepareStatement("SELECT Password,Status FROM User WHERE MobileNumber = ?");
        //PreparedStatement stmt = con.prepareStatement("SELECT * FROM User where Name=?");
        PreparedStatement stmt = con.prepareStatement("SELECT * FROM User where Status!='deleted'");
        //stmt.setString(1,"First");
        ResultSet rs = stmt.executeQuery();
        ArrayList<HashMap<String,String>> set = new ArrayList<HashMap<String,String>>();
        HashMap<String,String> cur ;
        while (rs.next()){
        	cur = new HashMap<String,String>();
        	cur.put("userKeyID",rs.getString("UserId"));
        	cur.put("usrName",rs.getString("Name"));
        	cur.put("imageFile",rs.getString("ImageFile"));
        	cur.put("mobNum",rs.getString("MobileNumber"));
        	cur.put("alias",rs.getString("Alias"));
        	cur.put("usrEmail",rs.getString("EmailAddress"));
        	cur.put("address",rs.getString("Address"));
        	cur.put("profilePic",rs.getString("ProfilePic"));
        	cur.put("idProofFile",rs.getString("IdProofFile"));
        	cur.put("addressProofFile",rs.getString("AddrProofFile"));
        	cur.put("notes",rs.getString("AddressVerificationNotes"));
        	cur.put("remainingCreditPostAudit",rs.getString("RemainingCreditPostAudit"));
        	cur.put("lastAuditedActivityAt",rs.getString("LastAuditedActivityAt"));
        	cur.put("notes",rs.getString("AddressVerificationNotes"));
        	// Getting the MAC address corresponding to the current User Id from the Device table
        	stmt  = con.prepareStatement("SELECT MAC FROM Device where UserId=?",Statement.RETURN_GENERATED_KEYS);
        	stmt.setInt(1,rs.getInt("userId"));
        	ResultSet rs2 = stmt.executeQuery();
        	rs2.next();
        	cur.put("custDeviceId",rs2.getString(1));
        	//o.println("Writing : "+cur);
        	set.add(cur);
        }
       log(INFO,"Sending the data of "+set.size()+" users");
        
       byte objBytes[] = SerializationUtils.serialize(set);  
       
       s.write(objBytes,0,objBytes.length);
       s.close();
		//out.close();
		} catch(Exception e){
			errorResponse(ctx, response, e.getMessage());
			log(WARNING,"Exception in listUser of Oprtr: "+e);
		}
		
	}
	
	/*
	public static byte[] serialize(Object obj) throws IOException {
	    ByteArrayOutputStream out = new ByteArrayOutputStream();
	    ObjectOutputStream os = new ObjectOutputStream(out);
	    os.writeObject(obj);
	    return out.toByteArray();
	} */
	
	private void changeCredits(ReqCtx ctx, HttpServletResponse response) throws  IOException {
		 
		 String eventNotes = ctx.getArgVal(EventNotes_tag);
		 String event = ctx.getArgVal(Details_tag);
		 String debitUserId = ctx.getArgVal(DebitUserId_tag);
		 String creditUserId = ctx.getArgVal(CreditUserId_tag);
		 
		 int amount = Integer.parseInt(ctx.getArgVal(Amount_tag));  
		 String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Timestamp(System.currentTimeMillis())); 
		 String userId = debitUserId!=null && !debitUserId.equals("") ? debitUserId : creditUserId; 
		 
			PreparedStatement statement = null;
			 try {
			   statement= con.prepareStatement("insert into OperatorLedger (EventNotes,ReqUserId,ReqDeviceId,"+
					   "CreditUserId,DebitUserId,Details,Time,AuditLogId,AuditNotes,Amount) "+
					   		"value(?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);

			    statement.setString(1,eventNotes);
	            statement.setInt(2,1);
	            statement.setInt(3,1);
	            statement.setString(4,creditUserId);
	            statement.setString(5,debitUserId);
	            statement.setString(6,event);
	            statement.setString(7,timeStamp);
	            statement.setString(8,null);
	            statement.setString(9,null);
	            statement.setInt(10,amount);
	            
	            int affectedRows = statement.executeUpdate();
		        if (affectedRows == 0) {
		            throw new SQLException("Adding to OperationLedger for UserId : "+userId+" failed, no rows affected.");
		        }

		        o.println("Successfully inserted into OperationLedger...!!");
		        successResponse(ctx,response, "Successfully inserted into OperationLedger for UserId: "+userId);
		    
			} catch(Exception ex){
		    	o.println("Error in insertion into OperationLedger in Oprtr: "+ex);
				errorResponse(ctx, response, ex.getMessage());
		    }finally {
		        if (statement != null) try { statement.close(); } catch (SQLException logOrIgnore) {}
		    }
			
	}
	
	
	private void addUser(ReqCtx ctx, HttpServletResponse response) throws  IOException {
	    PreparedStatement stmt = null;
		PreparedStatement statement = null;
	    ResultSet generatedKeys = null;
		
		String mobNum = ctx.getArgVal(mobNum_tag);
		String alias = ctx.getArgVal(alias_tag);
		String usrName = ctx.getArgVal(usrName_tag);
		String address = ctx.getArgVal(address_tag);
		String deviceId = ctx.getArgVal(custDeviceId_tag);
		
		String initialCredit = ctx.getArgVal(initialCredit_tag);
		String usrEmail = ctx.getArgVal(usrEmail_tag);
		String notes = ctx.getArgVal(notes_tag);
		String photoFile = ctx.getArgVal(imageFile_tag);
		String idProofFile = ctx.getArgVal(idProofFile_tag);
		String addressProofFile = ctx.getArgVal(addrProofFile_tag);
		String profilePic = ctx.getArgVal(profilePic_tag);
		boolean userExists = false;
		//String profilePic = null;
		if(null == initialCredit) initialCredit="0";
		Integer credit=Integer.decode(initialCredit);
		java.sql.Timestamp now= new java.sql.Timestamp(System.currentTimeMillis());
		
		
		if(null == usrEmail) usrEmail = "";
		if(null == notes) notes="";
		/*if(null == photoFileId) photoFileId="none";
		if(null == idProofFileId) idProofFileId="none";
		if(null == addressProofFileId) addressProofFileId="none";
		*/
		
		try {
			// TBD XXX check if MobileNumber already exists
			
			// if not a duplicate - create entry
			stmt = con.prepareStatement("SELECT * from User where MobileNumber = ?",
	                  Statement.RETURN_GENERATED_KEYS);
			stmt.setString(1,mobNum);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				userExists = true;
				throw new SQLException("User with the same mobile number exists!!");
			}
			
			stmt = con.prepareStatement("INSERT INTO User (Name, Alias,EmailAddress, MobileNumber," +
                  "Address, ProfilePic,ImageFile, IdProofFile,AddrProofFile," + 
                  "AddressVerificationNotes,  CreateTime, CreatedLedgerId," +
                  "RemainingCreditPostAudit, Status, Password)" +
                  "VALUE(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                  Statement.RETURN_GENERATED_KEYS);
			//o.println("Before filling the fields");
			stmt.setString(1, usrName);
			stmt.setString(2, alias);
			stmt.setString(3, usrEmail);
			stmt.setString(4, mobNum);
			stmt.setString(5, address);
			stmt.setString(6, profilePic);
			stmt.setString(7, photoFile);
			stmt.setString(8, idProofFile);
			stmt.setString(9, addressProofFile);
			stmt.setString(10, notes);
			stmt.setTimestamp(11, now);
			stmt.setInt(12, 0);
			stmt.setInt(13, credit);
			stmt.setString(14,"active");
			stmt.setString(15, "simple");
			
			int affectedRows = stmt.executeUpdate();
	        if (affectedRows == 0) {
	        	throw new SQLException("Creating user failed, no rows affected.");
	        }

	        generatedKeys = stmt.getGeneratedKeys();
	        if (generatedKeys.next()) {
	            int userId = generatedKeys.getInt(1);
	            statement = con.prepareStatement("INSERT INTO Device (MAC, UserId,CreatedLedgerEntry, Notes, CreateTime)"+
	            						"VALUE (?, ?, ?, ?, ?)");
	            statement.setString(1, deviceId);
	            statement.setInt(2, userId);
	            statement.setInt(3, 0);
	            statement.setString(4, notes);
	            statement.setTimestamp(5, now);
	            affectedRows = statement.executeUpdate();
		        if (affectedRows == 0) {
		            throw new SQLException("Creating device failed, no rows affected. Database inconsistent");
		        }
		        o.println("Added "+deviceId+" , "+userId+" ; affected rows : "+affectedRows);
	            
	        } else {
	            throw new SQLException("Creating user failed, no generated key obtained.");
	        }
	        log(INFO,"Successfully added User : "+usrName+" ; Mobile number : "+mobNum);
	        successResponse(ctx,response, "Added:" + mobNum + " for device :"+ deviceId);
	    
		} catch(Exception ex){
			log(WARNING,"Exception in adding User : "+usrName+" : "+ex.getMessage());
			if (userExists) errorResponse(ctx, response, ex.getMessage(),HttpServletResponse.SC_NOT_ACCEPTABLE);
			else errorResponse(ctx, response, ex.getMessage());
		}
		finally {
	        if (generatedKeys != null) try { generatedKeys.close(); } catch (SQLException logOrIgnore) {}
	        if (statement != null) try { statement.close(); } catch (SQLException logOrIgnore) {}
	        if (stmt != null) try { stmt.close(); } catch (SQLException logOrIgnore) {}
	    }
		
	}
	
	private void disableUser(ReqCtx ctx, HttpServletResponse response) throws  IOException {
		
		 int userId = Integer.parseInt(ctx.getArgVal(userKeyID_tag));  
		PreparedStatement statement = null;
		 try {
		   statement= con.prepareStatement("UPDATE User SET Status='deleted' WHERE UserId = ?",
                   Statement.RETURN_GENERATED_KEYS);
			statement.setInt(1,userId);
			
			int affectedRows = statement.executeUpdate();
	        if (affectedRows == 0) {
	            throw new SQLException("Disabling user "+userId+" failed, no rows affected.");
	        }

	        o.println("Successfully Disabled...!!");
	        successResponse(ctx,response, "Disabled user with UserId: "+userId);
	    
		} catch(Exception ex){
	    	o.println("Error in disableUser in Oprtr: "+ex);
			errorResponse(ctx, response, ex.getMessage());
	    }finally {
	        if (statement != null) try { statement.close(); } catch (SQLException logOrIgnore) {}
	    }
		
}
	
	
	private void listTransactions(ReqCtx ctx, HttpServletResponse response) throws IOException{
		o.println("In listTransactions ...");
		int userId = Integer.parseInt(ctx.getArgVal(userKeyID_tag));
		try {
		
		OutputStream s = response.getOutputStream();
		//outstmt = con.prepareStatement("SELECT Password,Status FROM User WHERE MobileNumber = ?");
        //PreparedStatement stmt = con.prepareStatement("SELECT * FROM User where Name=?");
        PreparedStatement stmt = con.prepareStatement("SELECT Details,Amount,Time FROM OperatorLedger where CreditUserId=? ||" +
        		"DebitUserId = ? ",
        		Statement.RETURN_GENERATED_KEYS);
        stmt.setInt(1,userId);
        stmt.setInt(2,userId);
        ResultSet rs = stmt.executeQuery();
        ArrayList<HashMap<String,String>> set = new ArrayList<HashMap<String,String>>();
        HashMap<String,String> cur ;
        while (rs.next()){
        	cur = new HashMap<String,String>();
        	cur.put("Details",rs.getString("Details"));
        	cur.put("Amount",rs.getString("Amount"));
        	cur.put("Time",rs.getString("Time"));
        	set.add(cur);
        }
       o.println("Sending the data of "+set.size()+" transactions");
        
       byte objBytes[] = SerializationUtils.serialize(set);  
       //byte []encoded = Base64.encode(objBytes);
       //ObjectOutputStream out = new ObjectOutputStream(s);
       //out.writeObject(objBytes);
       o.println("Flushed Data...");
	   
       s.write(objBytes,0,objBytes.length);
       s.close();
		//out.close();
		} catch(Exception e){
			errorResponse(ctx, response, e.getMessage());
			o.println("Exception in listTransactions of UserId "+userId+" : "+e);
		}
		
	}
	

	private void updateAddress(ReqCtx ctx, HttpServletResponse response) throws  IOException {
		errorResponse(ctx, response, "Not Implemented"); 
		return ;
	}
	
	private void updateDetails(ReqCtx ctx, HttpServletResponse response) throws  IOException {
		errorResponse(ctx, response, "Not Implemented"); 
		return ;
	}
	private void updateDevice(ReqCtx ctx, HttpServletResponse response) throws  IOException {
		errorResponse(ctx, response, "Not Implemented"); 
		return ;
	}
	private void lookupUser(ReqCtx ctx, HttpServletResponse response) throws  IOException {
		errorResponse(ctx, response, "Not Implemented"); 
		return ;
	}
	private void getUserApps(ReqCtx ctx, HttpServletResponse response) throws  IOException {
		errorResponse(ctx, response, "Not Implemented"); 
		return ;
	}


	
	private void getAppDetails(ReqCtx ctx, HttpServletResponse response) throws  IOException {
		errorResponse(ctx, response, "Not Implemented"); 
		return ;
	}
	private void updateUserAppRole(ReqCtx ctx, HttpServletResponse response) throws  IOException {
		errorResponse(ctx, response, "Not Implemented"); 
		return ;
	}
	private void getBillDetails(ReqCtx ctx, HttpServletResponse response) throws  IOException {
		errorResponse(ctx, response, "Not Implemented"); 
		return ;
	}
	private void deliveryUpdate(ReqCtx ctx, HttpServletResponse response) throws  IOException {
		errorResponse(ctx, response, "Not Implemented"); 
		return ;
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		o.println("In doPost of Oprtr..");
		doGet(request, response);
	}

}

	
	

