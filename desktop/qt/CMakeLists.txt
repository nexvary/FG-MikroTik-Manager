cmake_minimum_required(VERSION 3.20)
project(FgMtmDesktop VERSION 0.17.4 LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)
find_package(Qt6 6.8 REQUIRED COMPONENTS Quick QuickControls2 Network Test Widgets PrintSupport Sql Svg)
add_library(fg_core STATIC
 src/ApReport.cpp src/ApReport.hpp
 src/AccessPoints.cpp src/AccessPoints.hpp
 src/Bridge.cpp src/Bridge.hpp src/Protocol.cpp src/Protocol.hpp
 src/RouterClient.cpp src/RouterClient.hpp src/RouterJob.hpp
 src/Vouchers.cpp src/Vouchers.hpp src/Vault.cpp src/Vault.hpp
 src/Business.cpp src/Business.hpp src/BusinessCsv.cpp src/BusinessCsv.hpp
 src/RouterTools.cpp src/RouterTools.hpp src/Monitor.cpp src/Monitor.hpp
 src/NetworkBilling.cpp src/NetworkBilling.hpp vendor/qrcodegen.cpp vendor/qrcodegen.hpp)
target_include_directories(fg_core PUBLIC src)
target_link_libraries(fg_core PUBLIC Qt6::Network Qt6::Widgets Qt6::PrintSupport Qt6::Sql)
if(WIN32)
 target_link_libraries(fg_core PUBLIC crypt32 bcrypt)
endif()
set(portal_assets
 resources/hotspot/flogout.html resources/hotspot/flogin.html resources/hotspot/alogin.html
 resources/hotspot/md5.js resources/hotspot/status.html resources/hotspot/fstatus.html
 resources/hotspot/redirect.html resources/hotspot/fg.js resources/hotspot/rlogin.html
 resources/hotspot/logout.html resources/hotspot/rstatus.html resources/hotspot/fg.css
 resources/hotspot/login.html resources/hotspot/error.html)
qt_add_executable(fg_mtm WIN32 src/main.cpp)
set(icon_assets
 qml/icons/cloud.svg
 qml/icons/dashboard.svg
 qml/icons/developer.svg
 qml/icons/diagnostics.svg
 qml/icons/import.svg
 qml/icons/monitor.svg
 qml/icons/network.svg
 qml/icons/online.svg
 qml/icons/plans.svg
 qml/icons/renewal.svg
 qml/icons/router.svg
 qml/icons/sales.svg
 qml/icons/security.svg
 qml/icons/server.svg
 qml/icons/settings.svg
 qml/icons/subscribers.svg
 qml/icons/terminal.svg
 qml/icons/voucher.svg
)
qt_add_resources(fg_mtm "ui" PREFIX "/" FILES
 qml/AccessPointWorkspace.qml qml/Main.qml qml/FgDialog.qml qml/Theme.qml qml/FgButton.qml qml/FgField.qml qml/FgCombo.qml qml/NavIcon.qml
 qml/SubscriberWorkspace.qml qml/VoucherStudio.qml qml/BusinessWorkspace.qml qml/RouterToolsWorkspace.qml
 qml/MonitorWorkspace.qml qml/BusinessTransferWorkspace.qml qml/CommandLibrary.qml
 qml/NetworkBillingWorkspace.qml qml/AboutWorkspace.qml qml/HomeWorkspace.qml qml/FgTab.qml
 resources/business-schema.json resources/command-library.json resources/router-modules.json ${portal_assets} ${icon_assets} packaging/fg-mtm.png)
target_link_libraries(fg_mtm PRIVATE fg_core Qt6::Quick Qt6::QuickControls2 Qt6::Svg)
if(WIN32)
 configure_file(packaging/app.rc ${CMAKE_CURRENT_BINARY_DIR}/app.rc @ONLY)
 target_sources(fg_mtm PRIVATE ${CMAKE_CURRENT_BINARY_DIR}/app.rc)
endif()
enable_testing()
function(add_contract name source)
 qt_add_executable(${name}_tests ${source})
 target_link_libraries(${name}_tests PRIVATE fg_core Qt6::Test)
 qt_add_resources(${name}_tests "${name}_modules" PREFIX "/" FILES resources/router-modules.json)
 add_test(NAME ${name} COMMAND ${name}_tests -o ${name}-test.xml,junitxml)
endfunction()
add_contract(protocol tests/test_protocol.cpp)
add_contract(router tests/test_router.cpp)
add_contract(voucher tests/test_vouchers.cpp)
qt_add_resources(voucher_tests "vectors" PREFIX "/" FILES tests/backup-vector.hex)
add_contract(business tests/test_business.cpp)
qt_add_resources(business_tests "business_schema" PREFIX "/" FILES resources/business-schema.json)
add_contract(tool tests/test_tools.cpp)
qt_add_resources(tool_tests "portals" PREFIX "/" FILES ${portal_assets})
add_contract(monitor tests/test_monitor.cpp)
add_contract(billing tests/test_billing.cpp)
qt_add_resources(billing_tests "billing_schema" PREFIX "/" FILES resources/business-schema.json)

add_contract(cloud tests/test_cloud.cpp)
qt_add_resources(cloud_tests "cloud" PREFIX "/" FILES resources/business-schema.json resources/command-library.json tests/tls/ca.pem tests/tls/server.pem tests/tls/server-key.pem)

add_contract(accesspoints tests/test_accesspoints.cpp)
