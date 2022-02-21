#!/bin/bash
cmd=$(basename $0)

sub_help(){
    echo "Usage: $cmd <subcommand> [options]"
    echo "Subcommands:"
    echo "    help              Show help"
    echo "    EInvoiceGW        Check module EInvoiceGW"
    echo "    EInvoiceCommon    Check module EInvoiceCommon"
    echo "    EInvoiceWeb    Check module EInvoiceWeb"
    echo "    EinvoiceReportService    Check module EinvoiceReportService"
    echo "    MultitenancyHibernate    Check module MultitenancyHibernate"
    echo ""
}
sub_EInvoiceGW(){
    if [[ $(git diff HEAD~ --name-only | grep EInvoiceGW) = *EInvoiceGW/EInvoiceGW* ]]; then
        echo "change EInvoiceGW"
    fi
}
sub_EInvoiceCommon(){
    if [[ $(git diff HEAD~ --name-only | grep EInvoiceCommon) = *EInvoiceGW/EInvoiceCommon* ]]; then
        echo "change EInvoiceCommon"
    fi
}
sub_EInvoiceWeb(){
    if [[ $(git diff HEAD~ --name-only | grep EInvoiceWeb) = *EInvoiceWeb* ]]; then
        echo "change EInvoiceWeb"
    fi
}
sub_EinvoiceReportService(){
    if [[ $(git diff HEAD~ --name-only | grep EinvoiceReportService) = *EinvoiceReportService/Einvoice_Report_Service* ]]; then
        echo "change EinvoiceReportService"
    fi
}
sub_MultitenancyHibernate(){
    if [[ $(git diff HEAD~ --name-only | grep MultitenancyHibernate) = *MultitenancyHibernate* ]]; then
        echo "change MultitenancyHibernate"
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
