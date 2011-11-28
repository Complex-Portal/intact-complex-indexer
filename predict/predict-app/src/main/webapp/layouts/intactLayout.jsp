<%@ page language="java"%>
<%@ taglib uri="/WEB-INF/tld/struts-html.tld" prefix="html"%>
<%@ taglib uri="/WEB-INF/tld/struts-tiles.tld" prefix="tiles"%>

<%--
    Intact default look & feel layout. It consists of a sidebar and a display
    area. The display area conatns the header, contents and the footer as
    shown below:
    +---------------------+
    | Organization header +
    |---------------------+
    | side | header       +
    | bar  | contents     +
    |      | footer       +
    |---------------------+

    Author: Sugath Mudali (smudali@ebi.ac.uk)
    Version: $Id: intactLayout.jsp 5252 2006-07-05 14:29:22Z baranda $
--%>

<html:html>
<head>
    <meta http-equiv="cache-control" content="no-cache">
    <meta http-equiv="pragma" content="no-cache">
    <meta http-equiv="expires" content="-1">

    <!-- IntAct dynamic application should not be indexed by search engines -->
    <meta name='ROBOTS' content='NOINDEX'>

    <title><tiles:getAsString name="title"/></title>
    <!-- Don't put the base tag here; let the browser sort out the URLs -->
    <link rel="stylesheet" type="text/css"
        href="<%= request.getContextPath() %>/layouts/styles/intact.css"/>

    <!-- Needed to setup Tooltip for buttonBar.html. -->
    <script type="text/javascript" src="<%= request.getContextPath() %>/layouts/overLIB/overlib.js"></script>
</head>

<body bgcolor="#FFFFFF" topmargin="0" leftmargin="0">
<table border="0" height="100%" width="100%" cellspacing="5">

<tr>
    <%-- Sidebar section --%>
    <td bgcolor="#cccccc" width='113' valign='top' align='left'>
        <tiles:insert attribute="sidebar"/>
    </td>

    <td valign="top" height="100%" width="*">
        <table width="100%" height="100%">

            <%-- Application Header section --%>
            <tr>
                <td valign="top" height="5%">
                    <tiles:insert attribute="header"/>
                </td>

            </tr>
            <tr>
                <td height="3" background="<%=request.getContextPath()%>/images/hor.gif"></td>
            </tr>
            <%-- Content section --%>
            <tr>
                <td valign="top" height="*">
                    <!-- No errors if none specified -->
                    <tiles:insert attribute="content" ignore="true"/>
                </td>
            </tr>

            <%-- The footer --%>
            <tr>
                <td valign="bottom" height="10%">
                    <tiles:insert attribute="footer"/>
                </td>
            </tr>
        </table>
    </td>
</tr>
</table>
</body>
</html:html>
