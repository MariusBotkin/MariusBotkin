using System;
using System.Diagnostics;
using System.IO;
using System.Threading.Tasks;
using DSDPlusLauncher.Models;

namespace DSDPlusLauncher.Services
{
    public class ProcessRunner : IDisposable
    {
        public event EventHandler<string> LogReceived;
        public event EventHandler<int> ProcessExited;

        private Process _process;
        private bool _manualStop;

        public bool IsRunning
        {
            get
            {
                try
                {
                    return _process != null && !_process.HasExited;
                }
                catch
                {
                    return false;
                }
            }
        }

        public void Start(LauncherProfile profile)
        {
            if (profile == null)
            {
                throw new ArgumentNullException(nameof(profile));
            }

            if (IsRunning)
            {
                throw new InvalidOperationException("Ce profil est deja en cours d'execution.");
            }

            if (string.IsNullOrWhiteSpace(profile.ExecutablePath) || !File.Exists(profile.ExecutablePath))
            {
                throw new FileNotFoundException("Le chemin vers DSDPlus.exe est introuvable pour ce profil.", profile.ExecutablePath);
            }

            var workingDir = string.IsNullOrWhiteSpace(profile.WorkingDirectory)
                ? Path.GetDirectoryName(profile.ExecutablePath)
                : profile.WorkingDirectory;

            var psi = new ProcessStartInfo
            {
                FileName = profile.ExecutablePath,
                Arguments = profile.Arguments ?? string.Empty,
                WorkingDirectory = workingDir ?? string.Empty,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                CreateNoWindow = false
            };

            _manualStop = false;

            var process = new Process { StartInfo = psi, EnableRaisingEvents = true };
            process.OutputDataReceived += (_, e) =>
            {
                if (e.Data != null)
                {
                    LogReceived?.Invoke(this, e.Data);
                }
            };
            process.ErrorDataReceived += (_, e) =>
            {
                if (e.Data != null)
                {
                    LogReceived?.Invoke(this, e.Data);
                }
            };
            process.Exited += (_, _) =>
            {
                int exitCode;
                try { exitCode = process.ExitCode; } catch { exitCode = -1; }
                ProcessExited?.Invoke(this, exitCode);
            };

            process.Start();
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
            _process = process;

            if (profile.StartMinimized)
            {
                var capturedProcess = process;
                _ = Task.Run(async () =>
                {
                    for (var i = 0; i < 30; i++)
                    {
                        try
                        {
                            if (capturedProcess.HasExited)
                            {
                                return;
                            }

                            capturedProcess.Refresh();
                            if (capturedProcess.MainWindowHandle != IntPtr.Zero)
                            {
                                NativeMethods.MinimizeWindow(capturedProcess.MainWindowHandle);
                                return;
                            }
                        }
                        catch
                        {
                            return;
                        }

                        await Task.Delay(100).ConfigureAwait(false);
                    }
                });
            }
        }

        public bool WasManualStop => _manualStop;

        public void Stop()
        {
            if (!IsRunning)
            {
                return;
            }

            _manualStop = true;

            try
            {
                _process.CloseMainWindow();
                if (!_process.WaitForExit(3000))
                {
                    _process.Kill(true);
                }
            }
            catch
            {
                // Le processus s'est peut-etre deja termine entre-temps.
            }
        }

        public void Dispose()
        {
            _process?.Dispose();
        }
    }
}
