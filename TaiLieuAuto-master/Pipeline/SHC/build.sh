#!/bin/bash
cmd=$(basename $0)

sub_help(){
    echo "    Usage: $cmd <subcommand> [options]"
    echo "    Subcommands:"
    echo "    help               	    Show help"
    echo "    APIGateway        	    Check module APIGateway"
    echo "    SHCAdmin          	    Check module SHC ADMIN"
    echo "    AdminAuthenticationService    Check module AdminAuthenticationService"
    echo "    AdminServer    		    Check module AdminServer"
    echo "    AdminAuthoriseService    	    Check module AdminAuthoriseService"
    echo ""
}
sub_APIGateway(){
    if [[ $(git diff HEAD~ --name-only | grep "Servers/APIGateway") = *Servers/APIGateway* ]]; then
        echo "change APIGatway"
    fi
}
sub_SHCAdmin(){
    if [[ $(git diff HEAD~ --name-only | grep "Clients/Administrator") = *Clients/Administrator* ]]; then
        echo "change SHCAdmin"
    fi
}
sub_AdminAuthenticationService(){
    if [[ $(git diff HEAD~ --name-only | grep "Servers/Authentication Service") = *"Servers/Authentication Service"* ]]; then
        echo "change Authentication Service"
    fi
}
sub_AdminServer(){
    if [[ $(git diff HEAD~ --name-only | grep "Servers/Admin Server") = *"Servers/Admin Server"* ]]; then
        echo "change Admin Server"
    fi
}
sub_AdminAuthoriseService(){
    if [[ $(git diff HEAD~ --name-only | grep "Servers/Authorise Service") = *"Servers/Authorise Service"* ]]; then
        echo "change AdminAuthoriseService"
    fi
}
subcommand=$1

case $subcommand in
    "" | "-h" | "--help")
        sub_help
        ;;
    *)
        shift
        sub_${subcommand} $@
        if [ $? = 127 ]; then
            echo "Error: '$subcommand' is not a known subcommand." >&2
            echo "Run '$cmd --help' for a list of known subcommands." >&2
            exit 1
        fi
        ;;
esac
