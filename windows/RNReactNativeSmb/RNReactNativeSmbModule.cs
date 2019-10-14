using ReactNative.Bridge;
using System;
using System.Collections.Generic;
using Windows.ApplicationModel.Core;
using Windows.UI.Core;

namespace React.Native.Smb.RNReactNativeSmb
{
    /// <summary>
    /// A module that allows JS to share data.
    /// </summary>
    class RNReactNativeSmbModule : NativeModuleBase
    {
        /// <summary>
        /// Instantiates the <see cref="RNReactNativeSmbModule"/>.
        /// </summary>
        internal RNReactNativeSmbModule()
        {

        }

        /// <summary>
        /// The name of the native module.
        /// </summary>
        public override string Name
        {
            get
            {
                return "RNReactNativeSmb";
            }
        }
    }
}
