import * as React from "react"
import { cn } from "../../lib/utils"

function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type={type}
      className={cn("h-16 w-full rounded-xl border-2 border-input bg-background px-5 text-xl outline-none placeholder:text-muted-foreground focus-visible:ring-4 focus-visible:ring-ring/40 disabled:opacity-50", className)}
      {...props}
    />
  )
}

export { Input }
