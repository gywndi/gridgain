﻿/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

namespace Apache.Ignite.Core.Impl
{
    using System;
    using System.Diagnostics;
    using System.Diagnostics.CodeAnalysis;
    using Apache.Ignite.Core.Log;

    /// <summary>
    /// Shell utils (cmd/bash).
    /// </summary>
    [ExcludeFromCodeCoverage]
    internal static class Shell
    {
        /// <summary>
        /// Executes the command.
        /// </summary>
        [SuppressMessage("Microsoft.Design", "CA1031:DoNotCatchGeneralExceptionTypes",
            Justification = "ExecuteSafe should ignore all exceptions.")]
        public static string ExecuteSafe(string file, string args, int timeoutMs = 1000, ILogger log = null)
        {
            try
            {
                var processStartInfo = new ProcessStartInfo
                {
                    FileName = file,
                    Arguments = args,
                    RedirectStandardOutput = true,
                    UseShellExecute = false,
                    CreateNoWindow = true
                };

                using (var process = new Process {StartInfo = processStartInfo})
                {
                    process.Start();

                    if (!process.WaitForExit(timeoutMs))
                    {
                        log?.Warn("Shell command '{0}' timed out.", file);

                        process.Kill();
                    }

                    return process.StandardOutput.ReadToEnd();
                }
            }
            catch (Exception e)
            {
                log?.Warn("Shell command '{0}' failed: {1}", file, e.Message, e);

                return string.Empty;
            }
        }
    }
}
