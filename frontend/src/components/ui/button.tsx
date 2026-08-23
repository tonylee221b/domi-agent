import * as React from "react"
import { Slot } from "@radix-ui/react-slot"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "../../lib/utils"

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-xl text-lg font-medium transition-colors outline-none focus-visible:ring-4 focus-visible:ring-ring/40 disabled:pointer-events-none disabled:opacity-50 [&_svg]:size-5 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        default: "bg-primary text-primary-foreground hover:bg-primary/90",
        outline: "border-2 border-border bg-background hover:bg-muted",
        ghost: "hover:bg-muted text-primary",
        destructive: "bg-destructive text-white hover:bg-destructive/90",
      },
      size: {
        default: "h-14 px-6",
        sm: "h-11 px-4 text-base",
        lg: "h-16 px-8 text-xl",
        icon: "size-16",
      },
    },
    defaultVariants: { variant: "default", size: "default" },
  },
)

function Button({ className, variant, size, asChild = false, ...props }:
  React.ComponentProps<"button"> & VariantProps<typeof buttonVariants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot : "button"
  return <Comp className={cn(buttonVariants({ variant, size, className }))} {...props} />
}

export { Button, buttonVariants }
