Function IsLocked(strComputer)

    With GetObject("winmgmts:{impersonationLevel=impersonate}!\\" & strComputer & "\root\cimv2")
        IsLocked = .ExecQuery("select * from Win32_Process where Name='logonui.exe'").Count > 0
    End With

End Function

Function printf(txt)
	WScript.StdOut.WriteLine txt
End Function

Function printl(txt)
	WScript.StdOut.Write txt
End Function

If IsLocked(".") Then 
	printf "======>IDLE"
Else 
	printf "======>WORKING"
End If