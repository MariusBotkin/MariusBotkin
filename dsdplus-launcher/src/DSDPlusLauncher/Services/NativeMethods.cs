using System;
using System.Runtime.InteropServices;

namespace DSDPlusLauncher.Services
{
    internal static class NativeMethods
    {
        private const int SW_MINIMIZE = 6;

        [DllImport("user32.dll")]
        private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

        public static void MinimizeWindow(IntPtr handle)
        {
            if (handle != IntPtr.Zero)
            {
                ShowWindow(handle, SW_MINIMIZE);
            }
        }
    }
}
