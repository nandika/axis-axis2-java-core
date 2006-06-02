<%@ page import="org.apache.axis2.Constants,
                 org.apache.axis2.description.AxisOperation"%>
<%@ page import="org.apache.axis2.description.AxisService"%>
<%@ page import="java.util.Hashtable"%>
<%@ page import="java.util.Iterator"%>
<%@ page import="org.apache.axis2.transport.http.AxisServlet"%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<jsp:include page="include/adminheader.jsp"/>
<h1>List Single service</h1>
  <%
        String prifix = request.getAttribute("frontendHostUrl") + AxisServlet.SERVICE_PATH +"/";
        String restprefix = request.getAttribute("frontendHostUrl") + "rest/";
    %>
        <%
            String isFault = (String)request.getSession().getAttribute(Constants.IS_FAULTY);
            String servicName = request.getParameter("serviceName");
            if(Constants.IS_FAULTY.equals(isFault)){
                Hashtable errornessservices =(Hashtable)request.getSession().getAttribute(Constants.ERROR_SERVICE_MAP);
                %>
                    <h3>This Web axisService has deployment faults</h3><%
                     %><font color="red" ><%=(String)errornessservices.get(servicName) %></font>
                <%

                    }else {

                    AxisService axisService =
                            (AxisService) request.getSession().getAttribute(Constants.SINGLE_SERVICE);
                    if(axisService!=null){
           Iterator opItr = axisService.getOperations();
            //operationsList = operations.values();
          String  serviceName = axisService.getName();
            %><h2><font color="blue"><a href="<%=prifix + axisService.getName()%>?wsdl"><%=serviceName%></a></font></h2>
           <font color="blue">Service EPR : </font><font color="black"><%=prifix + axisService.getName()%></font><br>
               <font color="blue">Service REST epr :</font><font color="black"><%=restprefix + axisService.getName()%></font>
           <h4>Service Description : <font color="black"><%=axisService.getServiceDescription()%></h4>
           <i><font color="blue">Service Status : <%=axisService.isActive()?"Active":"InActive"%></font></i><br>
           <%
            if (opItr.hasNext()) {
                %><i>Available operations</i><%
            } else {
                %><i> There are no Operations specified</i><%
            }
               opItr = axisService.getOperations();
           %><ul><%
            while (opItr.hasNext()) {
                AxisOperation axisOperation = (AxisOperation) opItr.next();
                %><li><%=axisOperation.getName().getLocalPart()%></li>
<%--                <br>Operation EPR : <%=prifix + axisService.getName().getLocalPart() + "/"+ axisOperation.getName().getLocalPart()%>--%>
                <%
            }
           %></ul>
           <%
                    } else{
                           %>
                <h3><font color="red" >No service found in this location</font></h3>
 <%
                    }

            }
        %>
<jsp:include page="include/adminfooter.jsp"/>
