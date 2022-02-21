#!/bin/bash
cmd=$(basename $0)

sub_help(){
    echo "Usage: $cmd <subcommand> [options]"
    echo "Subcommands:"
    echo "    help              	Show help"
    echo "    services-chat      	Check module services-chat"
    echo "    services-facebook   	Check module services-facebook"
    echo "    services-ticket    	Check module services-ticket"
    echo ""
}
sub_services-chat(){
    if [[ $(git diff HEAD~ --name-only | grep services-chat) = *services-chat* ]]; then
        echo "change services-chat"
    fi
}
sub_services-facebook(){
    if [[ $(git diff HEAD~ --name-only | grep services-facebook) = *services-facebook* ]]; then
        echo "change services-facebook"
    fi
}
sub_services-ticket(){
    if [[ $(git diff HEAD~ --name-only | grep services-ticket) = *services-ticket* ]]; then
        echo "change services-ticket"
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
