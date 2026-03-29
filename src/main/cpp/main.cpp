#include <iostream>

#include "deployers/application_config_deployer.h"
#include "deployers/start_shell_deployer.h"
#include "installers/hakihive_jar_downloader.h"
#include "installers/java_runtime_installer.h"
#include "utils/input_util.h"

using std::cout;
using std::endl;
using std::cin;

int main()
{
    try
    {
        cout << "*** Hakihive one-click deployment CLI ***" << endl << endl;

        cout << "1. >>> Create start shell <<<" << endl;
        deployStartShell();

        cout << "2. >>> Download Hakihive jar <<<" << endl;
        downloadHakihiveJar();

        cout << "3. >>> Install Java Runtime Environment <<<" << endl;
        installJavaRuntime();

        cout << "5. >>> Create Spring configuration file <<<" << endl;
        deployApplicationConfig();

        cout << "Deployed successfully." << endl;
    } catch (std::runtime_error &e)
    {
        cout << "Deployed unsuccessfully because " << e.what() << "." << endl;
    }

    cout << "Type 'enter' to exit...";

    parse();
}
