using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;

namespace DSDPlusLauncher.Services
{
    public sealed class AudioDeviceInfo
    {
        public int Index { get; }
        public string Name { get; }

        public AudioDeviceInfo(int index, string name)
        {
            Index = index;
            Name = name;
        }

        public override string ToString() => $"{Index} : {Name}";
    }

    /// <summary>
    /// Enumere les peripheriques audio Windows (API MME / winmm.dll), dans le meme
    /// ordre que celui utilise par la plupart des logiciels audio historiques sous Windows.
    /// Sert uniquement a aider l'utilisateur a identifier le numero de peripherique
    /// a indiquer a DSDPlus ; le numero exact attendu par DSDPlus doit etre verifie
    /// dans la documentation fournie avec votre version.
    /// </summary>
    public static class AudioDeviceService
    {
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
        private struct WAVEINCAPS
        {
            public ushort wMid;
            public ushort wPid;
            public uint vDriverVersion;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)]
            public string szPname;
            public uint dwFormats;
            public ushort wChannels;
            public ushort wReserved1;
        }

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
        private struct WAVEOUTCAPS
        {
            public ushort wMid;
            public ushort wPid;
            public uint vDriverVersion;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)]
            public string szPname;
            public uint dwFormats;
            public ushort wChannels;
            public ushort wReserved1;
            public uint dwSupport;
        }

        [DllImport("winmm.dll")]
        private static extern uint waveInGetNumDevs();

        [DllImport("winmm.dll", CharSet = CharSet.Auto)]
        private static extern int waveInGetDevCaps(UIntPtr uDeviceID, ref WAVEINCAPS pwic, uint cbwic);

        [DllImport("winmm.dll")]
        private static extern uint waveOutGetNumDevs();

        [DllImport("winmm.dll", CharSet = CharSet.Auto)]
        private static extern int waveOutGetDevCaps(UIntPtr uDeviceID, ref WAVEOUTCAPS pwoc, uint cbwoc);

        public static List<AudioDeviceInfo> GetInputDevices()
        {
            var list = new List<AudioDeviceInfo>();
            try
            {
                var count = waveInGetNumDevs();
                for (uint i = 0; i < count; i++)
                {
                    var caps = new WAVEINCAPS();
                    var size = (uint)Marshal.SizeOf(typeof(WAVEINCAPS));
                    if (waveInGetDevCaps(new UIntPtr(i), ref caps, size) == 0)
                    {
                        list.Add(new AudioDeviceInfo((int)i, caps.szPname));
                    }
                }
            }
            catch
            {
                // Environnement sans API audio Windows disponible (ex : conception hors Windows).
            }

            return list;
        }

        public static List<AudioDeviceInfo> GetOutputDevices()
        {
            var list = new List<AudioDeviceInfo>();
            try
            {
                var count = waveOutGetNumDevs();
                for (uint i = 0; i < count; i++)
                {
                    var caps = new WAVEOUTCAPS();
                    var size = (uint)Marshal.SizeOf(typeof(WAVEOUTCAPS));
                    if (waveOutGetDevCaps(new UIntPtr(i), ref caps, size) == 0)
                    {
                        list.Add(new AudioDeviceInfo((int)i, caps.szPname));
                    }
                }
            }
            catch
            {
                // Environnement sans API audio Windows disponible (ex : conception hors Windows).
            }

            return list;
        }
    }
}
