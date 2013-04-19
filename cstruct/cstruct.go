package cstruct

import (
  "bytes"
  "encoding/gob"
  "fmt"
)

type Value struct {
  Buffer *bytes.Buffer
  Cmd []byte
}

func (v *Value) SetCommand(data string) {
  v.Cmd = []byte(data)
  //v.Serialize(data)
}

func (v Value) GetCommand() (data string) {
  return string(v.Cmd)
}

// FIXME: data is a string,need to be generic
// TODO: Use json instead gob because gob format is only readable with other Go code.
func (v *Value) Serialize(data string) {
  enc := gob.NewEncoder(v.Buffer)
  enc.Encode(data)
  fmt.Println(v.Buffer.Bytes())
//  return v.Buffer.Bytes()
}

func (v *Value) Deserialize() (data []string) {
  dec := gob.NewDecoder(v.Buffer)
  dec.Decode(&data)
  return data
}

type cstruct interface {
  Append(cmd Value)
  IsBottom() bool
}
